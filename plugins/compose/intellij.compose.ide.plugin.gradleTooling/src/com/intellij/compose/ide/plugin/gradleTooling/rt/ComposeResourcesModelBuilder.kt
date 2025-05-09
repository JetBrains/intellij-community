// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.gradleTooling.rt

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesExtension.Companion.composeResourcesExtension
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

class ComposeResourcesExtension private constructor(extension: Any) {
  private operator fun Any?.invoke(methodName: String): Any? {
    val klass = (this ?: return null)::class.java
    val method = klass.getMethodOrNull(methodName)
    return method?.invoke(this)
  }

  @Suppress("UNCHECKED_CAST")
  val customComposeResourcesDirectories: Map<String, Pair<String, Boolean>> by lazy {
    val invoke = extension("getCustomResourceDirectories\$compose") as? MutableMap<*, *> ?: emptyMap()
    invoke.entries.associate {
      val sourceSetName = it.key as String
      val directoryName = (it.value as? Provider<Directory>)?.orNull?.asFile?.path as String
      sourceSetName to (directoryName to /*isCustom*/ true)
    }.toMap()
  }

  val isPublicResClass: Boolean by lazy { extension("getPublicResClass") as? Boolean ?: false }

  val nameOfResClass: String by lazy { extension("getNameOfResClass") as? String ?: "Res" }

  companion object {
    val Project.composeResourcesExtension: ComposeResourcesExtension?
      get() = (this.extensions.findByName("compose") as? ExtensionAware)?.extensions?.findByName("resources")?.let(::ComposeResourcesExtension)
  }
}

class ComposeResourcesModelBuilder : ModelBuilderService {
  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(this)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle import errors")
      .withText("Unable to build Compose Resources Extensions plugin configuration")
      .withException(exception)
      .reportMessage(project)
  }

  override fun canBuild(modelName: String?): Boolean = modelName == ComposeResourcesModel::class.java.name

  override fun buildAll(modelName: String, project: Project): ComposeResourcesModel? {
    val resourcesExtension = project.composeResourcesExtension ?: return null
    return ComposeResourcesModelImpl(
      customComposeResourcesDirs = resourcesExtension.customComposeResourcesDirectories,
      isPublicResClass = resourcesExtension.isPublicResClass,
      nameOfResClass = resourcesExtension.nameOfResClass,
    )
  }
}
