// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.bundling.War
import org.jetbrains.plugins.gradle.model.web.WebConfiguration
import org.jetbrains.plugins.gradle.model.web.WebConfiguration.WebResource
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.web.WarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebResourceImpl
import java.io.ByteArrayOutputStream
import java.io.File

private val WAR_MODEL_NAME = WebConfiguration::class.java.name

private const val WEB_APP_DIR_PROPERTY = "webAppDir"
private const val WEB_APP_DIR_NAME_PROPERTY = "webAppDirName"

private val is82OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.2")

class WarModelBuilderImpl : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean {
    return WAR_MODEL_NAME == modelName
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
    project.plugins.findPlugin(WarPlugin::class.java) ?: return null

    val warModels = mutableListOf<WarModelImpl>()

    for (task in project.tasks.withType(War::class.java)) {

      val archiveFile = GradleTaskUtil.getTaskArchiveFile(task)
      val archiveName = GradleTaskUtil.getTaskArchiveFileName(task)

      val (webAppDir, webAppDirName) = collectWebAppDirAndName(project, task)
      val webXml = task.webXml
      val webResources = collectWebResources(context, project, task, webXml)
      val classpath = collectClasspath(context, project, task)
      val manifestContent = collectManifestContent(task)

      val warModel = WarModelImpl(archiveName, webAppDirName, webAppDir)
      warModel.archivePath = archiveFile
      warModel.webXml = webXml
      warModel.webResources = webResources
      warModel.classpath = classpath
      warModel.manifestContent = manifestContent
      warModels.add(warModel)
    }

    return WebConfigurationImpl(warModels)
  }

  private fun reportErrorMessage(context: ModelBuilderContext, project: Project, exception: Exception) {
    reportErrorMessage(WAR_MODEL_NAME, project, context, exception)
  }

  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(Messages.WAR_CONFIGURATION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("JEE project import failure")
      .withText("Web Facets/Artifacts may not be configured properly")
      .withException(exception)
      .reportMessage(project)
  }

  private fun collectWebAppDirAndName(project: Project, task: War): Pair<File, String> {
    if (is82OrBetter) {
      val webAppDir = task.webAppDirectory.asFile.get()
      val webAppDirName = webAppDir.name
      return webAppDir to webAppDirName
    }

    val webAppDirName = project.findProperty(WEB_APP_DIR_NAME_PROPERTY)?.toString()
                        ?: "src/main/webapp"
    val webAppDir = project.findProperty(WEB_APP_DIR_PROPERTY) as? File
                    ?: File(project.projectDir, webAppDirName)
    return webAppDir to webAppDirName
  }

  private fun collectWebResources(
    context: ModelBuilderContext,
    project: Project,
    task: War,
    webXml: File?,
  ): List<WebResource> {
    try {
      val webResources = ArrayList<WebResource>()
      CopySpecWalker.walk(task.rootSpec, object : CopySpecWalker.Visitor {
        override fun visitSourcePath(relativePath: String?, path: String) {
          val file = File(path).takeIf { it.isAbsolute } ?: File(task.project.projectDir, path)
          webResources.addWebResource(relativePath, "", file)
        }

        override fun visitDir(relativePath: String?, dirDetails: FileVisitDetails) {
          webResources.addWebResource(relativePath, dirDetails.path, dirDetails.file)
        }

        override fun visitFile(relativePath: String?, fileDetails: FileVisitDetails) {
          val file = fileDetails.file
          if (webXml == null || file.canonicalPath != webXml.canonicalPath) {
            webResources.addWebResource(relativePath, fileDetails.path, file)
          }
        }
      })
      return webResources
    }
    catch (e: Exception) {
      reportErrorMessage(context, project, e)
    }
    return emptyList()
  }

  private fun MutableList<WebResource>.addWebResource(
    warDirectory: String?,
    relativePath: String,
    file: File,
  ) {
    add(WebResourceImpl(warDirectory ?: "", relativePath, file))
  }

  private fun collectClasspath(context: ModelBuilderContext, project: Project, task: War): Set<File> {
    try {
      val classpath = task.classpath ?: return emptySet()
      return classpath.files.toSet()
    }
    catch (e: Exception) {
      reportErrorMessage(context, project, e)
    }
    return emptySet()
  }

  private fun collectManifestContent(task: War): String? {
    val manifest = task.manifest
    if (manifest !is ManifestInternal) return null
    val outputStream = ByteArrayOutputStream()
    manifest.writeTo(outputStream)
    val contentCharset = manifest.contentCharset
    return outputStream.toString(contentCharset)
  }
}