// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture

class GradleCodeInsightTestFixtureImpl private constructor(
  private val gradleFixture: GradleProjectTestFixture,
  override val codeInsightFixture: JavaCodeInsightTestFixture
) : GradleCodeInsightTestFixture, GradleProjectTestFixture by gradleFixture {

  constructor(fixture: GradleProjectTestFixture) : this(fixture, createCodeInsightFixture(fixture))

  override fun setUp() {
    gradleFixture.setUp()
    codeInsightFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { codeInsightFixture.tearDown() },
      { gradleFixture.tearDown() }
    )
  }

  companion object {
    private fun createCodeInsightFixture(gradleFixture: GradleProjectTestFixture): JavaCodeInsightTestFixture {
      val tempDirFixture = TempDirTestFixtureImpl()
      val projectFixture = object : IdeaProjectTestFixture {
        override fun getProject(): Project = gradleFixture.project
        override fun getModule(): Module = gradleFixture.module
        override fun setUp() = Unit
        override fun tearDown() = Unit
      }
      return object : JavaCodeInsightTestFixtureImpl(projectFixture, tempDirFixture) {
        override fun shouldTrackVirtualFilePointers(): Boolean = false

        init {
          setVirtualFileFilter(null)
        }
      }
    }
  }
}