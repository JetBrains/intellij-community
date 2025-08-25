// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.generator.JpsCompilationResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryValidator
import com.intellij.devkit.runtimeModuleRepository.jps.impl.DevkitRuntimeModuleRepositoryJpsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.platform.runtime.repository.serialization.impl.CompactFileWriter
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
import org.jetbrains.jps.model.java.JpsJavaExtensionService
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
    private val LOG = logger<RuntimeModuleRepositoryBuilder>()
  }

  override fun build(target: RuntimeModuleRepositoryTarget,
                     holder: DirtyFilesHolder<BuildRootDescriptor, RuntimeModuleRepositoryTarget>,
                     outputConsumer: BuildOutputConsumer,
                     context: CompileContext) {
    if (!holder.hasDirtyFiles() && !holder.hasRemovedFiles()) {
      LOG.debug("Runtime module repository is up to date")
      return
    }
    
    val project = target.project
    val descriptors: List<RawRuntimeModuleDescriptor>
    context.processMessage(ProgressMessage(DevkitRuntimeModuleRepositoryJpsBundle.message("progress.message.generating.intellij.modules.repository"), BuildTargetChunk(setOf(target))))
    val timeToCreateDescriptors = measureTimeMillis {
      val resourcePathsSchema = JpsCompilationResourcePathsSchema(project)
      descriptors = RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptors(project, resourcePathsSchema)
    }
    LOG.info("${descriptors.size} descriptors are created in ${timeToCreateDescriptors}ms")
    
    val errorReporter = object : RuntimeModuleRepositoryValidator.ErrorReporter {
      override fun reportDuplicatingId(moduleId: String) {
        context.reportError(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.duplicating.id.0.is.found", moduleId))
      }
    }
    RuntimeModuleRepositoryValidator.validate(descriptors, errorReporter) 
    
    val outputUrl = JpsJavaExtensionService.getInstance().getProjectExtension(project)?.outputUrl
    if (outputUrl.isNullOrEmpty()) {
      context.reportError(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.project.compiler.output.directory.is.not.specified"))
      return
    }
    val outputDir = Path.of(JpsPathUtil.urlToOsPath(outputUrl))
    val modulesXml = RuntimeModuleRepositoryTarget.getModulesXmlFile(project) ?: error("Project was not loaded from .idea")
    try {
      val jarRepositoryPath = outputDir.resolve(RuntimeModuleRepositoryGenerator.JAR_REPOSITORY_FILE_NAME)
      val timeToSaveDescriptorsToJar = measureTimeMillis {
        RuntimeModuleRepositorySerialization.saveToJar(descriptors, null, jarRepositoryPath, null, RuntimeModuleRepositoryGenerator.GENERATOR_VERSION)
      }
      outputConsumer.registerOutputFile(jarRepositoryPath.toFile(), listOf(modulesXml.absolutePath))
      LOG.info("${descriptors.size} descriptors are saved to JAR in ${timeToSaveDescriptorsToJar}ms")

      val compactRepositoryPath = outputDir.resolve(RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME)
      val timeToSaveDescriptorsToCompactFile = measureTimeMillis {
        CompactFileWriter.saveToFile(descriptors, null, null, RuntimeModuleRepositoryGenerator.GENERATOR_VERSION, compactRepositoryPath)
      }
      LOG.info("${descriptors.size} descriptors are saved in compact format in ${timeToSaveDescriptorsToCompactFile}ms")
      outputConsumer.registerOutputFile(compactRepositoryPath.toFile(), listOf(modulesXml.absolutePath))
    }
    catch (e: IOException) {
      LOG.info(e)
      context.reportError(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.failed.to.save.repository.0", e.message ?: ""))
    }
  }

  private fun CompileContext.reportError(message: @Nls String) {
    processMessage(CompilerMessage("intellij-runtime-repository", BuildMessage.Kind.ERROR, message))
  }



  override fun getPresentableName(): String {
    return DevkitRuntimeModuleRepositoryJpsBundle.message("builder.name.intellij.runtime.module.descriptors")
  }
}