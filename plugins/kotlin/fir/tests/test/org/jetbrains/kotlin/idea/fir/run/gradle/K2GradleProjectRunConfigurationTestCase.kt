// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.run.gradle

import com.intellij.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.kotlin.idea.run.gradle.KotlinGradleProjectRunConfigurationTestCase

@GradleProjectTestApplication
class K2GradleProjectRunConfigurationTestCase : KotlinGradleProjectRunConfigurationTestCase()