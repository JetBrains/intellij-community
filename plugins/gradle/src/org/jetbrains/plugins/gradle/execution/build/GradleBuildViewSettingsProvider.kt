// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.build.BuildViewSettingsProvider
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradleBuildViewSettingsProvider : BuildViewSettingsProvider {

  override fun isSingleBuildConsoleView(): Boolean = Registry.`is`("gradle.build.toolwindow.single.console")
}
