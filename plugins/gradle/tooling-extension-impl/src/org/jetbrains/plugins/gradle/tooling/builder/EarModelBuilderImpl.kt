// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ear.EarPlugin
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration.EarModel
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration.EarResource
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarResourceImpl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter

private val EAR_MODEL_NAME = EarConfiguration::class.java.name

private const val APP_DIR_NAME_PROPERTY = "appDirName"

private val is82OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.2")

class EarModelBuilderImpl : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean {
    return EAR_MODEL_NAME == modelName
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
    project.plugins.findPlugin(EarPlugin::class.java) ?: return null

    val earModels = ArrayList<EarModel>()

    val deployConfiguration = project.configurations.findByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)
    val earlibConfiguration = project.configurations.findByName(EarPlugin.EARLIB_CONFIGURATION_NAME)
    val earConfigurations = listOfNotNull(deployConfiguration, earlibConfiguration)
    val dependencyResolver = GradleDependencyResolver(context, project, GradleDependencyDownloadPolicy.NONE)
    val deployDependencies = dependencyResolver.resolveDependencies(deployConfiguration)
    val earlibDependencies = dependencyResolver.resolveDependencies(earlibConfiguration)
    val buildDirPath = GradleProjectUtil.getBuildDirectory(project).absolutePath

    for (task in project.tasks.withType(Ear::class.java)) {

      val archiveFile = GradleTaskUtil.getTaskArchiveFile(task)
      val archiveName = GradleTaskUtil.getTaskArchiveFileName(task)

      val appDirName = collectAppDirName(project, task)
      val libDirName = task.libDirName
      val earResources = collectEarResources(context, project, task, buildDirPath, earConfigurations)
      val deploymentDescriptor = collectDeploymentDescriptor(task)
      val manifestContent = collectManifestContent(task)

      val earModel = EarModelImpl(archiveName, appDirName, libDirName)
      earModel.resources = earResources
      earModel.deploymentDescriptor = deploymentDescriptor
      earModel.archivePath = archiveFile
      earModel.manifestContent = manifestContent
      earModels.add(earModel)
    }

    return EarConfigurationImpl(earModels, deployDependencies, earlibDependencies)
  }

  private fun reportErrorMessage(context: ModelBuilderContext, project: Project, exception: Exception) {
    reportErrorMessage(EAR_MODEL_NAME, project, context, exception)
  }

  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(Messages.EAR_CONFIGURATION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("JEE project import failure")
      .withText("Ear Facets/Artifacts may not be configured properly")
      .withException(exception)
      .reportMessage(project)
  }

  private fun collectAppDirName(project: Project, task: Ear): String {
    if (is82OrBetter) {
      val appDir = task.appDirectory.asFile.get()
      return appDir.absolutePath
    }
    return project.findProperty(APP_DIR_NAME_PROPERTY)?.toString() ?: "src/main/application"
  }

  private fun collectEarResources(
    context: ModelBuilderContext,
    project: Project,
    task: Ear,
    buildDirPath: String,
    earConfigurations: List<Configuration>,
  ): List<EarResource> {
    try {
      val earResources = ArrayList<EarResource>()
      CopySpecWalker.walk(task.rootSpec, object : CopySpecWalker.Visitor {
        override fun visitSourcePath(relativePath: String?, path: String) {
          val file = File(path).takeIf { it.isAbsolute } ?: File(task.project.projectDir, path)
          earResources.addEarResource(relativePath, "", file, buildDirPath, earConfigurations)
        }

        override fun visitDir(relativePath: String?, dirDetails: FileVisitDetails) {
          earResources.addEarResource(relativePath, dirDetails.path, dirDetails.file, buildDirPath, earConfigurations)
        }

        override fun visitFile(relativePath: String?, fileDetails: FileVisitDetails) {
          earResources.addEarResource(relativePath, fileDetails.path, fileDetails.file, buildDirPath, earConfigurations)
        }
      })
      return earResources
    }
    catch (e: Exception) {
      reportErrorMessage(context, project, e)
    }
    return emptyList()
  }

  private fun MutableList<EarResource>.addEarResource(
    earDirectory: String?,
    relativePath: String,
    file: File,
    buildDirPath: String,
    earConfigurations: List<Configuration>,
  ) {
    if (file.absolutePath.startsWith(buildDirPath)) {
      return
    }
    if (earConfigurations.any { file in it.files }) {
      return
    }
    add(EarResourceImpl(earDirectory ?: "", relativePath, file))
  }

  private fun collectDeploymentDescriptor(task: Ear): String? {
    val deploymentDescriptor = task.deploymentDescriptor ?: return null
    val writer = StringWriter()
    deploymentDescriptor.writeTo(writer)
    return writer.toString()
  }

  private fun collectManifestContent(task: Ear): String? {
    val manifest = task.manifest
    if (manifest !is ManifestInternal) return null
    val outputStream = ByteArrayOutputStream()
    manifest.writeTo(outputStream)
    val contentCharset = manifest.contentCharset
    return outputStream.toString(contentCharset)
  }
}