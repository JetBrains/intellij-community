// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFileName
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.bundling.War
import org.jetbrains.plugins.gradle.model.web.WebConfiguration
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.web.WarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebResourceImpl
import java.io.ByteArrayOutputStream
import java.io.File

private const val WEB_APP_DIR_PROPERTY = "webAppDir"
private const val WEB_APP_DIR_NAME_PROPERTY = "webAppDirName"
private val is82OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.2")

class WarModelBuilderImpl : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean {
    return WebConfiguration::class.java.name == modelName
  }


  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
    project.plugins.findPlugin(WarPlugin::class.java) ?: return null

    val warModels = mutableListOf<WarModelImpl>()

    for (task in project.tasks) {
      if (task is War) {
        val webAppDir: File
        val webAppDirName: String

        if (is82OrBetter) {
          webAppDir = task.webAppDirectory.asFile.get()
          webAppDirName = webAppDir.name
        }
        else {
          webAppDirName = if (!project.hasProperty(WEB_APP_DIR_NAME_PROPERTY)) "src/main/webapp" else project.property(WEB_APP_DIR_NAME_PROPERTY).toString()

          webAppDir = if (!project.hasProperty(WEB_APP_DIR_PROPERTY)) File(project.projectDir, webAppDirName) else project.property(WEB_APP_DIR_PROPERTY) as File
        }

        val warModel = WarModelImpl(getTaskArchiveFileName(task)!!, webAppDirName, webAppDir)

        val webResources = mutableListOf<WebConfiguration.WebResource>()
        val warTask = task as War
        warModel.webXml = warTask.webXml
        try {
          CopySpecWalker.walk(warTask.rootSpec, object : CopySpecWalker.Visitor {

            override fun visitSourcePath(relativePath: String?, path: String) {
              val file = File(path)
              addPath(webResources, relativePath, "", if (file.isAbsolute) file else File(warTask.project.projectDir, path))
            }


            override fun visitDir(relativePath: String?, dirDetails: FileVisitDetails) {
              addPath(webResources, relativePath, dirDetails.path, dirDetails.file)
            }


            override fun visitFile(relativePath: String?, fileDetails: FileVisitDetails) {
              if (warTask.webXml == null ||
                  !fileDetails.file.canonicalPath.equals(warTask.webXml?.canonicalPath)) {
                addPath(webResources, relativePath, fileDetails.path, fileDetails.file)
              }
            }
          })
          warModel.classpath = LinkedHashSet(warTask.classpath?.files)
        }
        catch (e: Exception) {
          reportErrorMessage(modelName, project, context, e)
        }

        warModel.webResources = webResources
        warModel.archivePath = getTaskArchiveFile(warTask)

        val manifest = warTask.manifest
        if (manifest is ManifestInternal) {
          val baos = ByteArrayOutputStream()
          manifest.writeTo(baos)
          warModel.manifestContent = baos.toString(manifest.contentCharset)
        }
        warModels.add(warModel)
      }
    }

    return WebConfigurationImpl(warModels)
  }


  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(Messages.WAR_CONFIGURATION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("JEE project import failure")
      .withText("Web Facets/Artifacts will not be configured properly")
      .withException(exception)
      .reportMessage(project)
  }

  companion object {

    private fun addPath(webResources: MutableList<WebConfiguration.WebResource>, warRelativePath: String?, fileRelativePath: String, file: File) {
      var warRelativePathOrEmpty = warRelativePath ?: ""

      val webResource = WebResourceImpl(warRelativePathOrEmpty, fileRelativePath, file)
      webResources.add(webResource)
    }
  }
}