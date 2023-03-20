// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

interface GradleCodeInsightTestFixture : GradleProjectTestFixture {

  val codeInsightFixture: JavaCodeInsightTestFixture
}