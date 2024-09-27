// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import org.gradle.api.Project
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
    val manifestAttributes = collectManifestAttributes(project)
    return JarTaskManifestConfigurationImpl(manifestAttributes)
  }

  private fun collectManifestAttributes(project: Project): Map<String, String> {
    val jarTasks = project.tasks.withType(Jar::class.java)
    val jarTask = jarTasks.findByName(JAR_TASK) ?: return emptyMap()
    return jarTask.manifest.attributes.mapValues { it.value.toString() }
  }
}