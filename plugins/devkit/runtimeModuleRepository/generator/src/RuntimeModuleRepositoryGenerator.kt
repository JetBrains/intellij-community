// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.enumerateRuntimeDependencies
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories

object RuntimeModuleRepositoryGenerator {
  const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
  const val COMPACT_REPOSITORY_FILE_NAME: String = "module-descriptors.dat"
  const val GENERATOR_VERSION: Int = 3

  /**
   * Generates the runtime module descriptors for all modules and libraries in [project].
   */
  fun generateRuntimeModuleDescriptorsForWholeProject(project: JpsProject, resourcePathsSchema: ResourcePathsSchema): List<RawRuntimeModuleDescriptor> {
    val projectLibraries = LinkedHashSet<JpsLibrary>()
    for (module in project.modules) {
      projectLibraries.addAll(enumerateRuntimeDependencies(module).libraries.filter { it.isProjectLevel })
    }
    return generateRuntimeModuleDescriptors(
      includedProduction = project.modules,
      includedTests = project.modules,
      includedProjectLibraries = projectLibraries,
      resourcePathsSchema = resourcePathsSchema
    )
  }

  /**
   * Generates the runtime module descriptors for production parts of [includedProduction], test parts of [includedTests] and 
   * [includedProjectLibraries].
   */
  fun generateRuntimeModuleDescriptors(includedProduction: Collection<JpsModule>,
                                       includedTests: Collection<JpsModule>,
                                       includedProjectLibraries: Collection<JpsLibrary>,
                                       resourcePathsSchema: ResourcePathsSchema): List<RawRuntimeModuleDescriptor> {
    val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
    generateDescriptorsForModules(descriptors, includedProduction, includedTests, resourcePathsSchema)
    for (library in includedProjectLibraries) {
      val moduleId = getProjectLibraryId(library) ?: error("Project-level library expected, but found: $library")
      descriptors.add(RawRuntimeModuleDescriptor.create(moduleId, resourcePathsSchema.libraryPaths(library), emptyList()))
    }
    return descriptors
  }

  fun saveModuleRepository(descriptors: List<RawRuntimeModuleDescriptor>, targetDirectory: Path) {
    try {
      val bootstrapModuleName = "intellij.platform.bootstrap"
      targetDirectory.createDirectories()
      val pluginHeaders = emptyList<RawRuntimePluginHeader>()
      RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors, pluginHeaders, bootstrapModuleName, targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
      RuntimeModuleRepositorySerialization.saveToJar(descriptors, pluginHeaders, bootstrapModuleName, targetDirectory.resolve(JAR_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to save runtime module repository: ${e.message}", e)
    }
  }

  fun enumerateRuntimeDependencies(module: JpsModule): JpsJavaDependenciesEnumerator {
    return JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly()
  }
}

private fun generateDescriptorsForModules(
  descriptors: MutableList<RawRuntimeModuleDescriptor>,
  includedProduction: Collection<JpsModule>,
  includedTests: Collection<JpsModule>,
  resourcePathsSchema: ResourcePathsSchema,
) {
  //it's better to get rid of such modules, but until it's done, we need to have this workaround to avoid duplicating IDs 
  val productionModulesWithTestRoots = HashSet<String>()
  val testModulesWithProductionRoots = HashSet<String>()
  val allIncludedTestModuleNames = includedTests.mapTo(HashSet()) { it.name }
  for (module in includedTests) {
    if (module.name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX) && module.hasProductionSources) {
      testModulesWithProductionRoots.add(module.name)
    }
    if ((module.name + RuntimeModuleId.TESTS_NAME_SUFFIX) in allIncludedTestModuleNames && module.hasTestSources) {
      productionModulesWithTestRoots.add(module.name)
    }
  }

  fun getRuntimeModuleName(module: JpsModule, tests: Boolean): RuntimeModuleId {
    val moduleName = module.name
    if (tests) {
      if (moduleName in productionModulesWithTestRoots) {
        return RuntimeModuleId.raw(moduleName + RuntimeModuleId.TESTS_NAME_SUFFIX + "2")
      }
      if (!moduleName.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
        return RuntimeModuleId.moduleTests(moduleName)
      }
    }
    else {
      if (moduleName in testModulesWithProductionRoots) {
        return RuntimeModuleId.raw(moduleName + "2")
      }
    }
    return RuntimeModuleId.module(moduleName)
  }

  for (module in includedProduction) {
    if (module.hasDescriptorForProduction) {
      descriptors.add(createProductionPartDescriptor(module, ::getRuntimeModuleName, resourcePathsSchema))
    }
  }
  if (includedTests.isNotEmpty()) {
    val additionalDependenciesForTestsCache = HashMap<JpsModule, DependenciesAndResources>()
    val productionDependenciesCache = HashMap<JpsModule, DependenciesAndResources>()
    for (module in includedTests) {
      if (module.hasTestSources) {
        descriptors.add(createTestPartDescriptor(module = module,
                                                 runtimeModuleNameGenerator = ::getRuntimeModuleName,
                                                 additionalDependenciesForTestsCache = additionalDependenciesForTestsCache,
                                                 productionDependenciesCache = productionDependenciesCache,
                                                 resourcePathsSchema = resourcePathsSchema))
      }
    }
  }
}

//if a module doesn't have production sources, it still makes sense to generate a descriptor for it, because it may be used from code
private val JpsModule.hasDescriptorForProduction: Boolean
  get() = !name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX) || hasProductionSources

private val JpsModule.hasTestSources
  get() = sourceRoots.any { it.rootType in JavaModuleSourceRootTypes.TESTS }

private val JpsModule.hasProductionSources
  get() = sourceRoots.any { it.rootType in JavaModuleSourceRootTypes.PRODUCTION }

private fun createProductionPartDescriptor(module: JpsModule, runtimeModuleNameGenerator: (JpsModule, Boolean) -> RuntimeModuleId, resourcePathsSchema: ResourcePathsSchema): RawRuntimeModuleDescriptor {
  val dependencies = LinkedHashSet<RuntimeModuleId>()
  val resourcePaths = if (module.hasProductionSources) resourcePathsSchema.moduleOutputPaths(module).toMutableSet() else mutableSetOf()
  enumerateRuntimeDependencies(module).productionOnly().processModuleAndLibraries(
    { dependencies.add(runtimeModuleNameGenerator(it, false)) },
    { library ->
      val projectLibraryId = getProjectLibraryId(library)
      if (projectLibraryId != null) {
        dependencies.add(projectLibraryId)
      }
      else {
        resourcePaths.addAll(resourcePathsSchema.libraryPaths(library))
      }
    }
  )
  return RawRuntimeModuleDescriptor.create(runtimeModuleNameGenerator(module, false), resourcePaths.toList(), dependencies.toList())
}

/**
 * Generates a descriptor for [module]'s tests.
 * In JPS, tests are added to the classpath transitively (see also MRI-2851).
 * For example, if module 'a' depends on 'b', and 'b' depends on 'c', then tests of module 'c' will be added to the test classpath of
 * module 'a', even if module 'b' has no test sources.
 * If we generate synthetic descriptors for tests of each module, even if it doesn't have test sources, the size of the module repository
 * will increase a lot. So here we add such transitive test dependencies directly to the module descriptors.
 */
private fun createTestPartDescriptor(
  module: JpsModule,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> RuntimeModuleId,
  additionalDependenciesForTestsCache: MutableMap<JpsModule, DependenciesAndResources>,
  productionDependenciesCache: MutableMap<JpsModule, DependenciesAndResources>,
  resourcePathsSchema: ResourcePathsSchema,
): RawRuntimeModuleDescriptor {
  val resourcePaths = if (module.hasTestSources) resourcePathsSchema.moduleTestOutputPaths(module).toMutableSet() else mutableSetOf()
  val dependencies = LinkedHashSet<RuntimeModuleId>()
  val forProduction = collectProductionDependenciesForModule(module, productionDependenciesCache, runtimeModuleNameGenerator, resourcePathsSchema)
  forProduction.copyTo(dependencies, resourcePaths)

  val forTests = collectAdditionalRuntimeDependenciesAndResourcesForTests(
    module,
    productionDependenciesCache,
    additionalDependenciesForTestsCache,
    runtimeModuleNameGenerator,
    resourcePathsSchema
  )
  forTests.copyTo(dependencies, resourcePaths)
  return RawRuntimeModuleDescriptor.create(runtimeModuleNameGenerator(module, true), resourcePaths.toList(), dependencies.toList())
}

/**
 * Returns IDs of descriptors which should be used as production runtime dependencies of [module].
 */
private fun collectProductionDependenciesForModule(
  module: JpsModule,
  productionDependenciesCache: MutableMap<JpsModule, DependenciesAndResources>,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> RuntimeModuleId,
  resourcePathsSchema: ResourcePathsSchema,
) : DependenciesAndResources {
  val cached = productionDependenciesCache[module]
  if (cached != null) {
    return cached
  }
  if (module.hasDescriptorForProduction) {
    val result = DependenciesAndResources(setOf(runtimeModuleNameGenerator(module, false)), emptySet())
    productionDependenciesCache[module] = result
    return result
  }
  
  //if a module doesn't have its own descriptor, its dependencies should be added instead
  productionDependenciesCache[module] = emptyDependenciesAndResources //to prevent StackOverflowError in case of circular dependencies
  val dependencies = LinkedHashSet<RuntimeModuleId>()
  val resourcePaths = LinkedHashSet<String>()
  enumerateRuntimeDependencies(module).productionOnly().processModuleAndLibraries(
    { dependency ->
      collectProductionDependenciesForModule(
        dependency,
        productionDependenciesCache,
        runtimeModuleNameGenerator,
        resourcePathsSchema
      ).copyTo(dependencies, resourcePaths)
    },
    { dependency ->
      val projectLibraryId = getProjectLibraryId(dependency)
      if (projectLibraryId != null) {
        dependencies.add(projectLibraryId)
      }
      else {
        resourcePaths.addAll(resourcePathsSchema.libraryPaths(dependency))
      }
    }
  )
  val result = DependenciesAndResources(dependencies, resourcePaths)
  productionDependenciesCache[module] = result
  return result
}

private data class DependenciesAndResources(
  val dependencies: Set<RuntimeModuleId>,
  val resourcePaths: Set<String>,
) {
  fun copyTo(dependencies: MutableSet<RuntimeModuleId>, resourcePaths: MutableCollection<String>) {
    dependencies.addAll(this.dependencies)
    resourcePaths.addAll(this.resourcePaths)
  }
}

private val emptyDependenciesAndResources = DependenciesAndResources(emptySet(), emptySet())

/**
 * Returns IDs of descriptors which should be added to tests dependencies of [module] in addition to production dependencies. 
 */
private fun collectAdditionalRuntimeDependenciesAndResourcesForTests(
  module: JpsModule,
  productionDependenciesCache: MutableMap<JpsModule, DependenciesAndResources>,
  additionalDependenciesForTestsCache: MutableMap<JpsModule, DependenciesAndResources>,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> RuntimeModuleId,
  resourcePathsSchema: ResourcePathsSchema
): DependenciesAndResources {
  val cached = additionalDependenciesForTestsCache[module]
  if (cached != null) {
    return cached
  }
  additionalDependenciesForTestsCache[module] = emptyDependenciesAndResources ////to prevent StackOverflowError in case of circular dependencies
  val dependencies = LinkedHashSet<RuntimeModuleId>()
  val resourcePaths = LinkedHashSet<String>()
  for (dependency in module.dependenciesList.dependencies) {
    when (dependency) {
      is JpsModuleDependency -> {
        val dependencyModule = dependency.module ?: continue
        val scope = dependency.scope
        if (scope == JpsJavaDependencyScope.PROVIDED) continue

        if (dependencyModule.hasTestSources) {
          dependencies.add(runtimeModuleNameGenerator(dependencyModule, true))
        }
        else {
          if (scope == JpsJavaDependencyScope.TEST) {
            collectProductionDependenciesForModule(
              dependencyModule,
              productionDependenciesCache,
              runtimeModuleNameGenerator,
              resourcePathsSchema
            ).copyTo(dependencies, resourcePaths)
          }
          collectAdditionalRuntimeDependenciesAndResourcesForTests(
            dependencyModule,
            productionDependenciesCache,
            additionalDependenciesForTestsCache,
            runtimeModuleNameGenerator,
            resourcePathsSchema
          ).copyTo(dependencies, resourcePaths)
        }
      }
      is JpsLibraryDependency -> {
        if (dependency.scope == JpsJavaDependencyScope.TEST) {
          dependency.library?.let { library ->
            val projectLibraryId = getProjectLibraryId(library)
            if (projectLibraryId != null) {
              dependencies.add(projectLibraryId)
            }
            else {
              resourcePaths.addAll(resourcePathsSchema.libraryPaths(library))
            }
          }
        }
      }
    }
  }
  val dependenciesAndResources = DependenciesAndResources(dependencies, resourcePaths)
  additionalDependenciesForTestsCache[module] = dependenciesAndResources
  return dependenciesAndResources
}

private val JpsDependencyElement.scope: JpsJavaDependencyScope?
  get() = JpsJavaExtensionService.getInstance().getDependencyExtension(this)?.scope

private fun getProjectLibraryId(library: JpsLibrary): RuntimeModuleId? {
  if (library.isProjectLevel) {
    return RuntimeModuleId.projectLibrary(library.name)
  }
  return null
}

val JpsLibrary.isProjectLevel: Boolean
  get() = (this as JpsElementBase<*>).parent.parent is JpsProject