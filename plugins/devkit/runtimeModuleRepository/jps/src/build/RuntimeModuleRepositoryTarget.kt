// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.dynatrace.hash4j.hashing.HashSink
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.COMPACT_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.impl.DevkitRuntimeModuleRepositoryJpsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.runtime.repository.serialization.impl.CompactFileReader
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer
import org.jetbrains.jps.builders.*
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import kotlin.system.measureTimeMillis

internal class RuntimeModuleRepositoryTarget(
  val project: JpsProject,
) : BuildTarget<BuildRootDescriptor>(RuntimeModuleRepositoryTarget), BuildTargetHashSupplier {
  override fun getId(): String {
    return "project"
  }

  override fun equals(other: Any?): Boolean {
    return other is RuntimeModuleRepositoryTarget
  }

  override fun hashCode(): Int {
    return 42
  }

  override fun computeDependencies(targetRegistry: BuildTargetRegistry, outputIndex: TargetOutputIndex): Collection<BuildTarget<*>> {
    return emptyList()
  }

  override fun computeRootDescriptors(model: JpsModel,
                                      index: ModuleExcludeIndex,
                                      ignoredFileIndex: IgnoredFileIndex,
                                      dataPaths: BuildDataPaths): List<BuildRootDescriptor> {
    /* runtime module repository uses data not only from modules.xml, but also from *.iml module and from .idea/libraries, 
       but there is no need to register them as sources because changes in them are handled via 'writeConfiguration' anyway;
       however, we need to have at least one source file to ensure that up-to-date checks can be properly performed. 
     */
    val modulesXmlFile = getModulesXmlFile(model.project)
    return modulesXmlFile?.let { listOf(BuildRootDescriptorImpl(this, it)) } ?: emptyList()
  }

  override fun findRootDescriptor(rootId: String, rootIndex: BuildRootIndex): BuildRootDescriptor? {
    return null
  }

  override fun getPresentableName(): String {
    return DevkitRuntimeModuleRepositoryJpsBundle.message("build.target.intellij.runtime.module.descriptors")
  }

  override fun getOutputRoots(context: CompileContext): Collection<File> {
    val project = context.projectDescriptor.project
    val outputUrl = JpsJavaExtensionService.getInstance().getProjectExtension(project)?.outputUrl ?: return emptyList()
    val outputDir = JpsPathUtil.urlToFile(outputUrl)
    return java.util.List.of(File(outputDir, JAR_REPOSITORY_FILE_NAME),
                             File(outputDir, COMPACT_REPOSITORY_FILE_NAME))
  }

  override fun computeConfigurationDigest(projectDescriptor: ProjectDescriptor, hash: HashSink) {
    hash.putString(JarFileSerializer.SPECIFICATION_VERSION)
    hash.putInt(CompactFileReader.FORMAT_VERSION)
    hash.putInt(RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION)

    val time = measureTimeMillis {
      computeDependenciesDigest(projectDescriptor, hash)
    }
    LOG.info("Dependencies digest computed in ${time}ms")
  }

  private fun computeDependenciesDigest(pd: ProjectDescriptor, hash: HashSink) {
    val relativizer = pd.dataManager.relativizer

    val modules = pd.project.modules
    for (module in modules) {
      hash.putString(module.name)
      val sourceRoots = module.sourceRoots
      for (sourceRoot in sourceRoots) {
        hash.putString(relativizer.toRelative(sourceRoot.path.toString()))
      }
      hash.putInt(sourceRoots.size)

      var counter = 0
      RuntimeModuleRepositoryBuilder.enumerateRuntimeDependencies(module).processModuleAndLibraries(
        {
          hash.putString(it.name)
          counter++
        },
        { library ->
          hash.putString(library.name)
          if (library.createReference().parentReference is JpsModuleReference) {
            updateFromRoots(library, hash, relativizer)
          }
          counter++
        },
      )
      hash.putInt(counter)
    }
    hash.putInt(modules.size)

    val libraries = pd.project.libraryCollection.libraries
    for (library in libraries) {
      hash.putString(library.name)
      updateFromRoots(library, hash, relativizer)
    }
    hash.putInt(libraries.size)
  }

  companion object : BuildTargetType<RuntimeModuleRepositoryTarget?>(RuntimeModuleRepositoryBuildConstants.TARGET_TYPE_ID), ModuleInducedTargetType {
    private val LOG = logger<RuntimeModuleRepositoryTarget>()
    
    override fun computeAllTargets(model: JpsModel): List<RuntimeModuleRepositoryTarget> {
      return if (isIntellijPlatformProject(model.project)) {
        listOf(RuntimeModuleRepositoryTarget(model.project))
      }
      else emptyList()
    }

    override fun createLoader(model: JpsModel): BuildTargetLoader<RuntimeModuleRepositoryTarget?> {
      return object : BuildTargetLoader<RuntimeModuleRepositoryTarget?>() {
        override fun createTarget(targetId: String): RuntimeModuleRepositoryTarget? {
          return if (isIntellijPlatformProject(model.project)) {
            RuntimeModuleRepositoryTarget(model.project)
          }
          else null
        }
      }
    }
    
    fun getModulesXmlFile(project: JpsProject): File? {
      val projectExtension = JpsModelSerializationDataService.getProjectExtension(project) ?: return null
      return File(projectExtension.baseDirectory, ".idea/modules.xml")
    }

    private fun isIntellijPlatformProject(project: JpsProject): Boolean {
      return project.findModuleByName("intellij.idea.community.main") != null || project.findModuleByName("intellij.platform.commercial") != null
    }

    private fun updateFromRoots(library: JpsLibrary, digest: HashSink, relativizer: PathRelativizerService) {
      val roots = library.getRoots(JpsOrderRootType.COMPILED)
      for (root in roots) {
        digest.putString(relativizer.toRelative(JpsPathUtil.urlToPath(root.url)))
      }
      digest.putInt(roots.size)
    }
  }
}