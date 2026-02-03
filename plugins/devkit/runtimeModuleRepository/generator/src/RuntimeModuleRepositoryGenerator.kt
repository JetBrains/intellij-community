// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.enumerateRuntimeDependencies
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
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
  const val GENERATOR_VERSION: Int = 2

  /**
   * Generates the runtime module descriptors for all modules and libraries in [project].
   */
  fun generateRuntimeModuleDescriptorsForWholeProject(project: JpsProject, resourcePathsSchema: ResourcePathsSchema): List<RawRuntimeModuleDescriptor> {
    val libraries = LinkedHashSet<JpsLibrary>()
    for (module in project.modules) {
      libraries.addAll(enumerateRuntimeDependencies(module).libraries)
    }
    return generateRuntimeModuleDescriptors(
      includedProduction = project.modules,
      includedTests = project.modules,
      includedLibraries = libraries,
      resourcePathsSchema = resourcePathsSchema
    )
  }

  /**
   * Generates the runtime module descriptors for production parts of [includedProduction], test parts of [includedTests] and 
   * [includedLibraries]. 
   */
  fun generateRuntimeModuleDescriptors(includedProduction: Collection<JpsModule>,
                                       includedTests: Collection<JpsModule>,
                                       includedLibraries: Collection<JpsLibrary>,
                                       resourcePathsSchema: ResourcePathsSchema): List<RawRuntimeModuleDescriptor> {
    val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
    generateDescriptorsForModules(descriptors, includedProduction, includedTests, resourcePathsSchema)
    for (library in includedLibraries) {
      val moduleId = getLibraryId(library)
      descriptors.add(RawRuntimeModuleDescriptor.create(moduleId.stringId, resourcePathsSchema.libraryPaths(library), emptyList()))
    }
    return descriptors
  }

  fun saveModuleRepository(descriptors: List<RawRuntimeModuleDescriptor>, targetDirectory: Path) {
    try {
      val bootstrapModuleName = "intellij.platform.bootstrap"
      targetDirectory.createDirectories()
      RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors, bootstrapModuleName, targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
      RuntimeModuleRepositorySerialization.saveToJar(descriptors, bootstrapModuleName, targetDirectory.resolve(JAR_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
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

  fun getRuntimeModuleName(module: JpsModule, tests: Boolean): String {
    val moduleName = module.name
    if (tests) {
      if (moduleName in productionModulesWithTestRoots) {
        return moduleName + RuntimeModuleId.TESTS_NAME_SUFFIX + "2"
      }
      if (!moduleName.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
        return moduleName + RuntimeModuleId.TESTS_NAME_SUFFIX
      }
    }
    else {
      if (moduleName in testModulesWithProductionRoots) {
        return moduleName + "2"
      }
    }
    return moduleName
  }

  for (module in includedProduction) {
    if (module.hasDescriptorForProduction) {
      descriptors.add(createProductionPartDescriptor(module, ::getRuntimeModuleName, resourcePathsSchema))
    }
  }
  if (includedTests.isNotEmpty()) {
    val additionalDependenciesForTestsCache = HashMap<JpsModule, Set<RuntimeModuleId>>()
    val productionDependenciesCache = HashMap<JpsModule, Set<RuntimeModuleId>>()
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

private fun createProductionPartDescriptor(module: JpsModule, runtimeModuleNameGenerator: (JpsModule, Boolean) -> String, resourcePathsSchema: ResourcePathsSchema): RawRuntimeModuleDescriptor {
  val dependencies = LinkedHashSet<String>()
  enumerateRuntimeDependencies(module).productionOnly().processModuleAndLibraries(
    { dependencies.add(runtimeModuleNameGenerator(it, false)) },
    { dependencies.add(getLibraryId(it).stringId) }
  )
  val resourcePaths = if (module.hasProductionSources) resourcePathsSchema.moduleOutputPaths(module) else emptyList()
  return RawRuntimeModuleDescriptor.create(runtimeModuleNameGenerator(module, false), resourcePaths, dependencies.toList())
}

/**
 * Generates a descriptor for [module]'s tests.
 * In JPS, tests are added to classpath transitively. For example, if module 'a' depends on 'b', and 'b' depends on 'c', then tests of
 * module 'c' will be added to test classpath of module 'a', even if module 'b' has no test sources.
 * If we generate synthetic descriptors for tests of each module, even if it doesn't have test sources, the size of the module repository
 * will increase a lot. So here we add such transitive test dependencies directly to the module descriptors.
 */
private fun createTestPartDescriptor(
  module: JpsModule,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> String,
  additionalDependenciesForTestsCache: MutableMap<JpsModule, Set<RuntimeModuleId>>,
  productionDependenciesCache: MutableMap<JpsModule, Set<RuntimeModuleId>>,
  resourcePathsSchema: ResourcePathsSchema,
): RawRuntimeModuleDescriptor {
  val dependencies = LinkedHashSet<RuntimeModuleId>()
  dependencies.addAll(collectProductionDependenciesForModule(module, productionDependenciesCache, runtimeModuleNameGenerator))
  dependencies.addAll(collectAdditionalRuntimeDependenciesForTests(module, productionDependenciesCache, additionalDependenciesForTestsCache, runtimeModuleNameGenerator))
  val resourcePaths = if (module.hasTestSources) resourcePathsSchema.moduleTestOutputPaths(module) else emptyList()
  return RawRuntimeModuleDescriptor.create(runtimeModuleNameGenerator(module, true), resourcePaths, dependencies.map { it.stringId })
}

/**
 * Returns IDs of descriptors which should be used as production runtime dependencies of [module].
 */
private fun collectProductionDependenciesForModule(
  module: JpsModule,
  productionDependenciesCache: MutableMap<JpsModule, Set<RuntimeModuleId>>,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> String,
): Set<RuntimeModuleId> {
  val cached = productionDependenciesCache[module]
  if (cached != null) {
    return cached
  }
  if (module.hasDescriptorForProduction) {
    val result = setOf(RuntimeModuleId.raw(runtimeModuleNameGenerator(module, false)))
    productionDependenciesCache[module] = result
    return result
  }
  
  //if a module doesn't have its own descriptor, its dependencies should be added instead
  productionDependenciesCache[module] = emptySet() //to prevent StackOverflowError in case of circular dependencies
  val result = LinkedHashSet<RuntimeModuleId>()
  enumerateRuntimeDependencies(module).productionOnly().processModuleAndLibraries(
    { dependency ->
      result.addAll(collectProductionDependenciesForModule(dependency, productionDependenciesCache, runtimeModuleNameGenerator))
    },
    { result.add(getLibraryId(it)) }
  )
  productionDependenciesCache[module] = result
  return result
}


/**
 * Returns IDs of descriptors which should be added to tests dependencies of [module] in addition to production dependencies. 
 */
private fun collectAdditionalRuntimeDependenciesForTests(
  module: JpsModule,
  productionDependenciesCache: MutableMap<JpsModule, Set<RuntimeModuleId>>,
  additionalDependenciesForTestsCache: MutableMap<JpsModule, Set<RuntimeModuleId>>,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> String
): Collection<RuntimeModuleId> {
  val cached = additionalDependenciesForTestsCache[module]
  if (cached != null) {
    return cached
  }
  additionalDependenciesForTestsCache[module] = emptySet() ////to prevent StackOverflowError in case of circular dependencies 
  val result = LinkedHashSet<RuntimeModuleId>()
  for (dependency in module.dependenciesList.dependencies) {
    when (dependency) {
      is JpsModuleDependency -> {
        val dependencyModule = dependency.module ?: continue
        val scope = dependency.scope
        if (scope == JpsJavaDependencyScope.PROVIDED) continue

        if (dependencyModule.hasTestSources) {
          result.add(RuntimeModuleId.raw(runtimeModuleNameGenerator(dependencyModule, true)))
        }
        else {
          if (scope == JpsJavaDependencyScope.TEST) {
            result.addAll(collectProductionDependenciesForModule(dependencyModule, productionDependenciesCache, runtimeModuleNameGenerator))
          }
          result.addAll(collectAdditionalRuntimeDependenciesForTests(dependencyModule, productionDependenciesCache, additionalDependenciesForTestsCache, runtimeModuleNameGenerator))
        }
      }
      is JpsLibraryDependency -> {
        if (dependency.scope == JpsJavaDependencyScope.TEST) {
          dependency.library?.let { result.add(getLibraryId(it)) }
        }
      }
    }
  }
  additionalDependenciesForTestsCache[module] = result
  return result
}

private val JpsDependencyElement.scope: JpsJavaDependencyScope?
  get() = JpsJavaExtensionService.getInstance().getDependencyExtension(this)?.scope

private fun getLibraryId(library: JpsLibrary): RuntimeModuleId {
  var name = library.name
  val element = (library as JpsElementBase<*>).parent.parent
  if (element is JpsModule) {
    val files = library.getFiles(JpsOrderRootType.COMPILED)
    if (name.startsWith("#") && files.size == 1) {
      name = files[0].name
    }
    return RuntimeModuleId.moduleLibrary((element as JpsModule).name, name)
  }
  return RuntimeModuleId.projectLibrary(name)
}
