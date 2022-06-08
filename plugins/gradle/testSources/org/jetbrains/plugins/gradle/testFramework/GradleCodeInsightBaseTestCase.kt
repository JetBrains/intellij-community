// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.jetbrains.plugins.groovy.util.BaseTest

abstract class GradleCodeInsightBaseTestCase : GradleTestCase(), BaseTest {

  abstract override fun createGradleTestFixture(gradleVersion: GradleVersion): GradleCodeInsightTestFixture

  val codeInsightFixture: JavaCodeInsightTestFixture
    get() = (gradleFixture as GradleCodeInsightTestFixture).codeInsightFixture

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture
}
