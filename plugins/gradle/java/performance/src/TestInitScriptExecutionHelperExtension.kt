// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Adds the Gradle init script that the system property [INIT_SCRIPT_PROPERTY] names to each Gradle execution of the IDE.
 * A test sets the property to give an init script to the IDE under test only.
 * The `init.d` directory of the Gradle user home would give it to every Gradle build of the machine.
 */
internal class TestInitScriptExecutionHelperExtension : GradleExecutionHelperExtension {

  override fun configureSettings(settings: GradleExecutionSettings, context: GradleExecutionContext) {
    val initScript = System.getProperty(INIT_SCRIPT_PROPERTY) ?: return
    if (initScript in settings.arguments) return
    settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript)
  }

  companion object {
    const val INIT_SCRIPT_PROPERTY: String = "idea.gradle.test.init.script"
  }
}
