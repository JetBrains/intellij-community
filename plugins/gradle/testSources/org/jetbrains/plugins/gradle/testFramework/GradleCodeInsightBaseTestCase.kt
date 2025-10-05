// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.groovy.util.BaseTest

abstract class GradleCodeInsightBaseTestCase : GradleProjectTestCase(), BaseTest {

  private var _codeInsightFixture: JavaCodeInsightTestFixture? = null
  val codeInsightFixture: JavaCodeInsightTestFixture
    get() = requireNotNull(_codeInsightFixture) {
      "Gradle code insight fixture wasn't setup. Please use [AbstractGradleCodeInsightBaseTestCase.test] function inside your tests."
    }

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture

  override fun setUp() {
    super.setUp()

    _codeInsightFixture = GradleCodeInsightTestFixture(gradleFixture)
    codeInsightFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _codeInsightFixture?.tearDown() },
      { _codeInsightFixture = null },
      { super.tearDown() }
    )
  }

  private class GradleDataProvider(private val gradleFixture: GradleProjectTestFixture) : DataProvider {
    @Override
    override fun getData(dataId: String): Any? =
      when {
        CommonDataKeys.PROJECT.`is`(dataId) -> gradleFixture.project
        CommonDataKeys.EDITOR.`is`(dataId) -> FileEditorManager.getInstance(gradleFixture.project).getSelectedTextEditor()
        else -> null
      }
  }

  private class GradleIdeaProjectTestFixture(private val gradleFixture: GradleProjectTestFixture) : IdeaProjectTestFixture {

    private lateinit var testDisposable: Disposable

    override fun getProject(): Project = gradleFixture.project
    override fun getModule(): Module = gradleFixture.module
    override fun getTestRootDisposable(): Disposable = testDisposable

    override fun setUp() {
      testDisposable = Disposer.newDisposable()
      TestApplicationManager.getInstance().setDataProvider(GradleDataProvider(gradleFixture), testDisposable)
    }

    override fun tearDown() {
      Disposer.dispose(testDisposable)
    }
  }

  private class GradleCodeInsightTestFixture(
    gradleFixture: GradleProjectTestFixture
  ) : JavaCodeInsightTestFixtureImpl(
    GradleIdeaProjectTestFixture(gradleFixture),
    TempDirTestFixtureImpl()
  ) {

    override fun shouldTrackVirtualFilePointers(): Boolean = false

    init {
      setVirtualFileFilter(null)
    }

    override fun setUp() {
      super.setUp()
      GradleBuildClasspathManager.getInstance(project).reload()
    }
  }
}