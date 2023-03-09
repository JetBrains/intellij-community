// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.groovy.util.BaseTest

abstract class GradleCodeInsightBaseTestCase : GradleProjectTestCase(), BaseTest {

  val codeInsightFixture: JavaCodeInsightTestFixture
    get() = (gradleFixture as GradleCodeInsightTestFixture).codeInsightFixture

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture

  override fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    super.test(gradleVersion, GradleCodeInsightTestFixtureBuilder(fixtureBuilder), test)
  }

  protected fun testEmptyProject(gradleVersion: GradleVersion, test: () -> Unit) =
    test(gradleVersion, GradleTestFixtureBuilder.EMPTY_PROJECT, test)

  protected fun testJavaProject(gradleVersion: GradleVersion, test: () -> Unit) =
    test(gradleVersion, GradleTestFixtureBuilder.JAVA_PROJECT, test)


  private class GradleCodeInsightTestFixtureBuilder(private val builder: GradleTestFixtureBuilder) : GradleTestFixtureBuilder {

    override val projectName: String by builder::projectName

    override fun createFixture(gradleVersion: GradleVersion): GradleCodeInsightTestFixture {
      val gradleFixture = builder.createFixture(gradleVersion)
      val fixtureFactory = GradleTestFixtureFactory.getFixtureFactory()
      return fixtureFactory.createGradleCodeInsightTestFixture(gradleFixture)
    }
  }
}
