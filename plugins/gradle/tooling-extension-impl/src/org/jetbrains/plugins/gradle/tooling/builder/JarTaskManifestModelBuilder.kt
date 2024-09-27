// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.model.taskIndex.GradleTaskIndex
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import org.gradle.api.Project
import org.gradle.api.java.archives.Attributes
import org.gradle.jvm.tasks.Jar
import org.jetbrains.plugins.gradle.model.jar.JarTaskManifestConfiguration
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.jar.JarTaskManifestConfigurationImpl

private const val JAR_TASK = "jar"

class JarTaskManifestModelBuilder : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean {
    return JarTaskManifestConfiguration::class.java.name == modelName
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any {
    val tasks = GradleTaskIndex.getInstance(context)
      .getAllTasks(project)

    val projectIdentityPathToManifestAttributes = HashMap<String, Map<String, String>>()
    for (task in tasks) {
      if (task is Jar && JAR_TASK == task.name) {
        val attributes = task.manifest.attributes
        if (!attributes.isEmpty()) {
          projectIdentityPathToManifestAttributes.put(identityPath(project), attributeMap(attributes))
        }
      }
    }
    return JarTaskManifestConfigurationImpl(projectIdentityPathToManifestAttributes)
  }

  private fun attributeMap(attributes: Attributes): Map<String, String> {
    val result = HashMap<String, String>()
    for (entry in attributes.entries) {
      result.put(entry.key, entry.value.toString())
    }
    return result
  }

  private fun identityPath(project: Project): String {
    // composite builds
    val identityPath = GradleProjectUtil.getProjectIdentityPath(project)
    return if (identityPath == null || ":" == identityPath) project.path else identityPath
  }
}