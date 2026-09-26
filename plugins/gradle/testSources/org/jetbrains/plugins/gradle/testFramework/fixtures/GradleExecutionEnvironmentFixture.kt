// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.testFramework.fixtures.IdeaTestFixture

interface GradleExecutionEnvironmentFixture : IdeaTestFixture {

  fun getExecutionEnvironment(): ExecutionEnvironment
}