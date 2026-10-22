// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.junit.JUnitRunnerSelectionPolicy
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project

/**
 * TeamCity runs JUnit 3 and JUnit 4 tests on the JUnit Platform with the Vintage engine.
 * Local runs do the same, to avoid differences between local runs and TeamCity runs.
 */
internal class DevKitJUnitRunnerSelectionPolicy : JUnitRunnerSelectionPolicy {
  override fun isPackageRunnerPreferred(project: Project, module: Module?): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project)
  }
}
