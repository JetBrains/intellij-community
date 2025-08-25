// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.GENERATE_DESCRIPTORS_FOR_TEST_MODULES
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.enumerateRuntimeDependencies
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency

object RuntimeModuleRepositoryGenerator {
  const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
  const val COMPACT_REPOSITORY_FILE_NAME: String = "module-descriptors.dat"
  const val GENERATOR_VERSION: Int = 2

  /**
   * Generates the runtime module descriptors for all modules and libraries in [project].
   */
  fun generateRuntimeModuleDescriptors(project: JpsProject, resourcePathsSchema: ResourcePathsSchema): List<RawRuntimeModuleDescriptor> {
    val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
    generateDescriptorsForModules(descriptors, resourcePathsSchema, project)
    val libraries = LinkedHashSet<JpsLibrary>()
    for (module in project.modules) {
      libraries.addAll(enumerateRuntimeDependencies(module).libraries)
    }
    for (library in libraries) {
      val moduleId = getLibraryId(library)
      descriptors.add(RawRuntimeModuleDescriptor.create(moduleId.stringId, resourcePathsSchema.libraryPaths(library), emptyList()))
    }
    return descriptors
  }

  fun enumerateRuntimeDependencies(module: JpsModule): JpsJavaDependenciesEnumerator {
    return JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly()
  }

  /**
   * Specifies whether descriptors for 'tests' parts of modules should be generated.
   */
  const val GENERATE_DESCRIPTORS_FOR_TEST_MODULES: Boolean = true
}

private fun generateDescriptorsForModules(descriptors: MutableList<RawRuntimeModuleDescriptor>, resourcePathsSchema: ResourcePathsSchema, project: JpsProject) {
  //it's better to get rid of such modules, but until it's done, we need to have this workaround to avoid duplicating IDs 
  val productionModulesWithTestRoots = HashSet<String>()
  val testModulesWithProductionRoots = HashSet<String>()
  val allModuleNames = project.modules.mapTo(HashSet()) { it.name }
  for (module in project.modules) {
    if (module.name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX) && module.hasProductionSources) {
      testModulesWithProductionRoots.add(module.name)
    }
    if ((module.name + RuntimeModuleId.TESTS_NAME_SUFFIX) in allModuleNames && module.hasTestSources) {
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

  for (module in project.modules) {
    //if a module doesn't have production sources, it still makes sense to generate a descriptor for it, because it may be used from code
    if (!module.name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX) || module.hasProductionSources) {
      descriptors.add(createProductionPartDescriptor(module, ::getRuntimeModuleName, resourcePathsSchema))
    }
    if (GENERATE_DESCRIPTORS_FOR_TEST_MODULES && module.hasTestSources) {
      descriptors.add(createTestPartDescriptor(module, ::getRuntimeModuleName, resourcePathsSchema))
    }
  }
}

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
 * will increase a lot. So here we add such transitive test dependencies directly to the module descriptors. To avoid adding too many
 * dependencies, we add only those which aren't already available as transitive dependencies of explicitly added dependencies.
 */
private fun createTestPartDescriptor(module: JpsModule, runtimeModuleNameGenerator: (JpsModule, Boolean) -> String, resourcePathsSchema: ResourcePathsSchema): RawRuntimeModuleDescriptor {
  val addedTransitiveModuleDependencies = HashSet<JpsModule>()
  val addedTransitiveLibraryDependencies = HashSet<JpsLibrary>()

  fun JpsJavaDependenciesEnumerator.collectTransitiveDependencies() {
    recursively().satisfying { dependency ->
      (dependency as? JpsModuleDependency)?.module !in addedTransitiveModuleDependencies
    }.processModuleAndLibraries(
      { addedTransitiveModuleDependencies.add(it) },
      { addedTransitiveLibraryDependencies.add(it) }
    )
  }

  JpsJavaExtensionService.dependencies(module).runtimeOnly().processModules { directDependency ->
    if (directDependency.hasTestSources) {
      JpsJavaExtensionService.dependencies(module).withoutSdk().runtimeOnly().collectTransitiveDependencies()
    }
  }
  JpsJavaExtensionService.dependencies(module).withoutSdk().runtimeOnly().productionOnly().collectTransitiveDependencies()
  addedTransitiveModuleDependencies.remove(module)

  val dependencies = LinkedHashSet<String>()
  val processedDummyTestDependencies = HashSet<String>()
  if (module.hasProductionSources) {
    dependencies.add(runtimeModuleNameGenerator(module, false))
  }
  enumerateRuntimeDependencies(module).processModuleAndLibraries(
    { dependency ->
      if (dependency.hasProductionSources) {
        dependencies.add(runtimeModuleNameGenerator(dependency, false))
      }
      addTestDependency(dependencies, dependency, processedDummyTestDependencies, addedTransitiveModuleDependencies,
                        addedTransitiveLibraryDependencies, runtimeModuleNameGenerator)
    },
    { dependencies.add(getLibraryId(it).stringId) }
  )
  val resourcePaths = if (module.hasTestSources) resourcePathsSchema.moduleTestOutputPaths(module) else emptyList()
  return RawRuntimeModuleDescriptor.create(runtimeModuleNameGenerator(module, true), resourcePaths, dependencies.toList())
}

private fun addTestDependency(
  result: MutableCollection<String>,
  module: JpsModule,
  processedDummyTestDependencies: HashSet<String>,
  addedTransitiveModuleDependencies: MutableSet<JpsModule>,
  addedTransitiveLibraryDependencies: MutableSet<JpsLibrary>,
  runtimeModuleNameGenerator: (JpsModule, Boolean) -> String
) {
  if (module.hasTestSources) {
    result.add(runtimeModuleNameGenerator(module, true))
    return
  }
  if (!processedDummyTestDependencies.add(module.name)) {
    return
  }
  enumerateRuntimeDependencies(module).processModuleAndLibraries(
    {
      addTestDependency(result, it, processedDummyTestDependencies, addedTransitiveModuleDependencies,
                        addedTransitiveLibraryDependencies, runtimeModuleNameGenerator)
    },
    {
      if (addedTransitiveLibraryDependencies.add(it)) {
        result.add(getLibraryId(it).stringId)
      }
    }
  )
}

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
