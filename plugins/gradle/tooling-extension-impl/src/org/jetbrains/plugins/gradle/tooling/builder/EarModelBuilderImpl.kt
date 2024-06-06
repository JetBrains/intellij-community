// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFileName
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ear.EarPlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarResourceImpl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter

class EarModelBuilderImpl : AbstractModelBuilderService() {

  private val APP_DIR_PROPERTY = "appDirName"
  private val is82OrBetter: Boolean = GradleVersionUtil.isCurrentGradleAtLeast("8.2")

  override fun canBuild(modelName: String): Boolean {
    return EarConfiguration::class.java.name == modelName
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
    // https://issues.apache.org/jira/browse/GROOVY-9555
    val earPlugin = project.plugins.findPlugin(EarPlugin::class.java)
    if (earPlugin == null) return null

    val earModels = arrayListOf<EarConfiguration.EarModel>()
    val deployConfiguration = project.configurations.findByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)
    val earlibConfiguration = project.configurations.findByName(EarPlugin.EARLIB_CONFIGURATION_NAME)
    val dependencyResolver = GradleDependencyResolver(context, project, GradleDependencyDownloadPolicy.NONE)
    val deployDependencies = dependencyResolver.resolveDependencies(deployConfiguration)
    val earlibDependencies = dependencyResolver.resolveDependencies(earlibConfiguration)
    val buildDirPath = GradleProjectUtil.getBuildDirectory(project).absolutePath

    for (task in project.tasks) {
      if (task is Ear) {
        val appDirName: String
        if (is82OrBetter) {
          val appDirectoryLocation = GradleReflectionUtil.reflectiveGetProperty(task, "getAppDirectory", Object::class.java)
          appDirName = GradleReflectionUtil.reflectiveCall(appDirectoryLocation, "getAsFile", File::class.java).absolutePath
        }
        else {
          appDirName = if (!project.hasProperty(APP_DIR_PROPERTY)) "src/main/application" else project.property(APP_DIR_PROPERTY).toString()
        }

        val earModel = EarModelImpl(getTaskArchiveFileName(task)!!, appDirName, task.libDirName)

        val earResources = arrayListOf<EarConfiguration.EarResource>()
        val earTask = task as Ear

        try {
          CopySpecWalker.walk(earTask.rootSpec, object : CopySpecWalker.Visitor {
            override fun visitSourcePath(relativePath: String, path: String) {
              val file = File(path)
              addPath(buildDirPath, earResources, relativePath, "",
                      if (file.isAbsolute) file else File(earTask.project.projectDir, path),
                      deployConfiguration, earlibConfiguration)
            }

            override fun visitDir(relativePath: String, dirDetails: FileVisitDetails) {
              addPath(buildDirPath, earResources, relativePath, dirDetails.path, dirDetails.file, deployConfiguration, earlibConfiguration)
            }

            override fun visitFile(relativePath: String, fileDetails: FileVisitDetails) {
              addPath(buildDirPath, earResources, relativePath, fileDetails.path,
                      fileDetails.file, deployConfiguration, earlibConfiguration)
            }
          })
        }
        catch (e: Exception) {
          reportErrorMessage(modelName, project, context, e)
        }

        earModel.resources = earResources

        val deploymentDescriptor = earTask.deploymentDescriptor
        if (deploymentDescriptor != null) {
          val writer = StringWriter()
          deploymentDescriptor.writeTo(writer)
          earModel.deploymentDescriptor = writer.toString()
        }
        earModel.archivePath = getTaskArchiveFile(earTask)

        val manifest = earTask.manifest
        if (manifest is ManifestInternal) {
          val outputStream = ByteArrayOutputStream()
          manifest.writeTo(outputStream)
          val contentCharset = manifest.contentCharset
          earModel.manifestContent = outputStream.toString(contentCharset)
        }

        earModels.add(earModel)
      }
    }
    return EarConfigurationImpl(earModels, deployDependencies, earlibDependencies)
  }

  override fun reportErrorMessage(modelName: @NotNull String,
                                  project: @NotNull Project,
                                  context: @NotNull ModelBuilderContext,
                                  exception: @NotNull Exception) {
    context.messageReporter.createMessage()
      .withGroup(Messages.EAR_CONFIGURATION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("JEE project import failure")
      .withText("Ear Artifacts may not be configured properly")
      .withException(exception)
      .reportMessage(project)
  }

  private fun addPath(buildDirPath: String,
                      earResources: MutableList<EarConfiguration.EarResource>,
                      earRelativePath: String?,
                      fileRelativePath: String,
                      file: File,
                      vararg earConfigurations: Configuration?) {

    if (file.absolutePath.startsWith(buildDirPath)) return

    for (conf in earConfigurations) {
      if (conf?.files?.contains(file) == true) return
    }

    val earResource = EarResourceImpl(earRelativePath ?: "", fileRelativePath, file)
    earResources.add(earResource)
  }
}