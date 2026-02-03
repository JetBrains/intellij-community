// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultIntelliJSettings
import org.jetbrains.plugins.gradle.model.IntelliJSettings
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

/**
 * @author Vladislav.Soroka
 */
class IntelliJSettingsBuilder : ModelBuilderService {

  override fun canBuild(modelName: String): Boolean {
    return modelName == IntelliJSettings::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    val extensionAware = project.plugins.findPlugin(IdeaPlugin::class.java)?.model?.module as ExtensionAware?
    if (extensionAware != null) {
      val obj = extensionAware.extensions.findByName("settings")
      if (obj != null) {
        return DefaultIntelliJSettings(obj.toString())
      }
    }
    return null
  }

  override fun reportErrorMessage(
    @NotNull modelName: String,
    @NotNull project: Project,
    @NotNull context: ModelBuilderContext,
    @NotNull exception: Exception
  ) {
    context.messageReporter.createMessage()
      .withGroup(Messages.INTELLIJ_SETTINGS_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("IntelliJ settings import failure")
      .withText("Unable to build IntelliJ settings")
      .withException(exception)
      .reportMessage(project)
  }
}
