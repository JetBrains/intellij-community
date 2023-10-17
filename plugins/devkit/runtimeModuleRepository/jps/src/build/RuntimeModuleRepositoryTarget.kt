// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.impl.DevkitRuntimeModuleRepositoryJpsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer
import com.intellij.util.io.DigestUtil
import org.jetbrains.jps.builders.*
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.PrintWriter
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

internal class RuntimeModuleRepositoryTarget(val project: JpsProject) : BuildTarget<BuildRootDescriptor?>(RuntimeModuleRepositoryTarget) {
  override fun getId(): String {
    return "project"
  }

  override fun computeDependencies(targetRegistry: BuildTargetRegistry, outputIndex: TargetOutputIndex): Collection<BuildTarget<*>> {
    return emptyList()
  }

  override fun computeRootDescriptors(model: JpsModel,
                                      index: ModuleExcludeIndex,
                                      ignoredFileIndex: IgnoredFileIndex,
                                      dataPaths: BuildDataPaths): List<BuildRootDescriptor> {
    return emptyList()
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
    return listOf(File(JpsPathUtil.urlToFile(outputUrl), JAR_REPOSITORY_FILE_NAME))
  }

  override fun writeConfiguration(pd: ProjectDescriptor, out: PrintWriter) {
    val digest: ByteArray
    val time = measureTimeMillis {
      digest = computeDependenciesDigest(pd)
    }
    LOG.info("Dependencies digest computed in ${time}ms")
    out.println("${JarFileSerializer.SPECIFICATION_VERSION}.${RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION}")
    out.println(StringUtil.toHexString(digest))
  }

  private fun computeDependenciesDigest(pd: ProjectDescriptor): ByteArray {
    val digest = DigestUtil.sha256()
    val relativizer = pd.dataManager.relativizer
    fun MessageDigest.update(string: String) {
      update(string.encodeToByteArray())
      update('\n'.code.toByte())
    }

    fun MessageDigest.updateFromRoots(library: JpsLibrary) {
      library.getRoots(JpsOrderRootType.COMPILED).forEach {
        update(relativizer.toRelative(JpsPathUtil.urlToPath(it.url)))
      }
    }
    pd.project.modules.forEach { module ->
      digest.update(module.name)
      module.sourceRoots.forEach { sourceRoot ->
        digest.update(relativizer.toRelative(sourceRoot.file.absolutePath))
      }
      RuntimeModuleRepositoryBuilder.enumerateRuntimeDependencies(module).processModuleAndLibraries(
        {
          digest.update(it.name)
        },
        { library ->
          digest.update(library.name)
          if (library.createReference().parentReference is JpsModuleReference) {
            digest.updateFromRoots(library)
          }
        },
      )
    }
    pd.project.libraryCollection.libraries.forEach { library ->
      digest.update(library.name)
      digest.updateFromRoots(library)
    }
    return digest.digest()
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
    
    private fun isIntellijPlatformProject(project: JpsProject): Boolean {
      return project.modules.any { it.name == "intellij.idea.community.main" || it.name == "intellij.platform.commercial" }
    }
  }
}
