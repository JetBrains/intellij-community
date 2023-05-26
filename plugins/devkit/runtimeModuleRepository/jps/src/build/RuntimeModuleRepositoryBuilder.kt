// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.impl.DevkitRuntimeModuleRepositoryJpsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.builders.BuildOutputConsumer
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.TargetBuilder
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Generates JAR file for [com.intellij.platform.runtime.repository.RuntimeModuleRepository] during compilation.
 */
internal class RuntimeModuleRepositoryBuilder 
  : TargetBuilder<BuildRootDescriptor, RuntimeModuleRepositoryTarget>(listOf(RuntimeModuleRepositoryTarget)) {
  companion object {
    /**
     * Specifies whether descriptors for 'tests' parts of modules should be generated.  
     */
    const val GENERATE_DESCRIPTORS_FOR_TEST_MODULES = false
    private val LOG = logger<RuntimeModuleRepositoryBuilder>()

    internal fun enumerateRuntimeDependencies(module: JpsModule): JpsJavaDependenciesEnumerator {
      return JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly()
    }
  }

  override fun build(target: RuntimeModuleRepositoryTarget,
                     holder: DirtyFilesHolder<BuildRootDescriptor, RuntimeModuleRepositoryTarget>,
                     outputConsumer: BuildOutputConsumer,
                     context: CompileContext) {
    val project = target.project
    val descriptors: List<RawRuntimeModuleDescriptor>
    context.processMessage(ProgressMessage(DevkitRuntimeModuleRepositoryJpsBundle.message("progress.message.generating.intellij.modules.repository"), BuildTargetChunk(setOf(target))))
    val timeToCreateDescriptors = measureTimeMillis {
      descriptors = createDescriptors(project, context.projectDescriptor.dataManager.relativizer)
    }
    LOG.info("${descriptors.size} descriptors are created in ${timeToCreateDescriptors}ms")
    
    RuntimeModuleRepositoryValidator.validate(descriptors) { context.reportError(it) } 
    
    val outputUrl = JpsJavaExtensionService.getInstance().getProjectExtension(project)?.outputUrl
    if (outputUrl.isNullOrEmpty()) {
      context.reportError(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.project.compiler.output.directory.is.not.specified"))
      return
    }
    val outputPath = Path.of(JpsPathUtil.urlToOsPath (outputUrl), JAR_REPOSITORY_FILE_NAME)
    val timeToSaveDescriptors = measureTimeMillis {
      try {
        RuntimeModuleRepositorySerialization.saveToJar(descriptors, null, outputPath, GENERATOR_VERSION)
      }
      catch (e: IOException) {
        LOG.info(e)
        context.reportError(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.failed.to.save.jar.file.0", e.message ?: ""))
      }
    }
    LOG.info("${descriptors.size} descriptors are saved in ${timeToSaveDescriptors}ms")
  }

  private fun CompileContext.reportError(message: @Nls String) {
    processMessage(CompilerMessage("intellij-runtime-repository", BuildMessage.Kind.ERROR, message))
  }

  private fun createDescriptors(project: JpsProject, relativizer: PathRelativizerService): List<RawRuntimeModuleDescriptor> {
    val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
    generateDescriptorsForModules(descriptors, project)
    val libraries = LinkedHashSet<JpsLibrary>()
    for (module in project.modules) {
      libraries.addAll(enumerateRuntimeDependencies(module).libraries)
    }
    for (library in libraries) {
      val moduleId = getLibraryId(library)
      val files = library.getFiles(JpsOrderRootType.COMPILED)
      descriptors.add(RawRuntimeModuleDescriptor(moduleId.stringId, files.map { relativizer.toRelative(it.absolutePath) }, emptyList()))
    }
    return descriptors
  }

  private fun generateDescriptorsForModules(descriptors: MutableList<RawRuntimeModuleDescriptor>, project: JpsProject) {
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
      if (!module.isTestOnly) {
        descriptors.add(createModuleDescriptor(module, false, ::getRuntimeModuleName))
      }
      if (GENERATE_DESCRIPTORS_FOR_TEST_MODULES && module.hasTestSources) {
        descriptors.add(createModuleDescriptor(module, true, ::getRuntimeModuleName))
      }
    }
  }
  
  private val JpsModule.isTestOnly
    get() = name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX) ||
            //todo align module names to get rid of these conditions
            name.endsWith("all-tests") || name.endsWith(".test") || name.endsWith(".tests.main") || name.contains(".tests.") ||
            name.endsWith("Tests") ||
            name in setOf("kotlin.jvm-debugger.test.k2", "intellij.devkit.testFramework", "intellij.jupyter.testFramework",
                          "kotlin-ultimate.spring-tests", "intellij.goland.tools", "intellij.kotlin.testsWithAndroidPlugin", "fleet")
   

  private val JpsModule.hasTestSources
    get() = sourceRoots.any { it.rootType in JavaModuleSourceRootTypes.TESTS }

  private val JpsModule.hasProductionSources
    get() = sourceRoots.any { it.rootType in JavaModuleSourceRootTypes.PRODUCTION }

  private fun createModuleDescriptor(module: JpsModule, test: Boolean, runtimeModuleNameGenerator: (JpsModule, Boolean) -> String): RawRuntimeModuleDescriptor {
    val dependencies = LinkedHashSet<String>()
    val processedDummyTestDependencies = HashSet<String>()
    collectDependencies(module, test, dependencies, processedDummyTestDependencies, runtimeModuleNameGenerator)
    val sourceRootTypes = if (test) JavaModuleSourceRootTypes.TESTS else JavaModuleSourceRootTypes.PRODUCTION
    val resourcePaths = if (module.sourceRoots.any { it.rootType in sourceRootTypes }) {
      listOf("${if (test) "test" else "production"}/${module.name}")
    }
    else {
      emptyList()
    }
    return RawRuntimeModuleDescriptor(runtimeModuleNameGenerator(module, test), resourcePaths, dependencies.toList())
  }

  private fun collectDependencies(module: JpsModule,
                                  test: Boolean,
                                  result: MutableCollection<String>,
                                  processedDummyTestDependencies: HashSet<String>,
                                  runtimeModuleNameGenerator: (JpsModule, Boolean) -> String) {
    val enumerator = enumerateRuntimeDependencies(module)
    if (!test) {
      enumerator.productionOnly()
    }
    else if (module.hasProductionSources) {
      result.add(runtimeModuleNameGenerator(module, false))
    }
    enumerator.processModuleAndLibraries(
      { addDependency(result, it, test, processedDummyTestDependencies, runtimeModuleNameGenerator) },
      { result.add(getLibraryId(it).stringId) }
    )
  }

  private fun addDependency(result: MutableCollection<String>,
                            module: JpsModule,
                            test: Boolean,
                            processedDummyTestDependencies: HashSet<String>,
                            runtimeModuleNameGenerator: (JpsModule, Boolean) -> String) {
    if (!test || module.hasTestSources) {
      result.add(runtimeModuleNameGenerator(module, test))
      return
    }
    if (!processedDummyTestDependencies.add(module.name)) {
      return
    }
    collectDependencies(module, true, result, processedDummyTestDependencies, runtimeModuleNameGenerator)
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


  override fun getPresentableName(): String {
    return DevkitRuntimeModuleRepositoryJpsBundle.message("builder.name.intellij.runtime.module.descriptors")
  }
}