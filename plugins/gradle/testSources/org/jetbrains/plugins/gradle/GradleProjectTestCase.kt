// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.platform.externalSystem.testFramework.ExternalSystemProjectTestCase
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings

abstract class GradleProjectTestCase : ExternalSystemProjectTestCase() {
  override fun createProjectSettings() = GradleProjectSettings()
}