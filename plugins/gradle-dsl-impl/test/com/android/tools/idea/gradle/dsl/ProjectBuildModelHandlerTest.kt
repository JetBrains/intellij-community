/*
 * Copyright (C) 2018 The Android Open Source myProject
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.registerExtension
import org.junit.Test
import org.mockito.Mockito.*

class ProjectBuildModelHandlerTest : UsefulTestCase() {
  lateinit var projectBuildModel: ProjectBuildModel
  lateinit var myFixture: JavaCodeInsightTestFixture
  lateinit var myProject: Project
  lateinit var myUpToDateChecker: UpToDateChecker

  override fun setUp() {
    super.setUp()
    val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())
    myFixture.setUp()
    myProject = myFixture.project
    projectBuildModel = mock(ProjectBuildModel::class.java)
    myUpToDateChecker = mock(UpToDateChecker::class.java)
    ApplicationManager.getApplication().registerExtension(UpToDateChecker.EXTENSION_POINT_NAME, myUpToDateChecker, testRootDisposable)

  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun setupUpToDate(upToDate: Boolean) {
    `when`(myUpToDateChecker.checkUpToDate(any())).thenReturn(upToDate)
  }


  @Test
  fun testReuseExistingModel() {
    setupUpToDate(true)
    val handler = ProjectBuildModelHandlerImpl(myProject, projectBuildModel)
    val buildModel = projectBuildModel
    handler.read {
      assertTrue(buildModel === this)
    }
    handler.modify {
      assertTrue(buildModel === this)
    }
  }

  @Test
  fun testRecreateModelsIfNotUpToDate() {
    setupUpToDate(false)
    val handler = ProjectBuildModelHandlerImpl(myProject, projectBuildModel)
    var buildModel = projectBuildModel
    handler.modify {
      assertFalse(buildModel === this)
      buildModel = this
    }
    handler.read {
      assertFalse(buildModel === this)
    }
  }

  @Test
  fun testRecreateModelOnNewSync() {
    setupUpToDate(true)
    val handler = ProjectBuildModelHandlerImpl(myProject, projectBuildModel)
    setupUpToDate(false)
    var buildModel = projectBuildModel
    handler.read {
      assertFalse(buildModel === this)
      buildModel = this
    }
    setupUpToDate(false)
    handler.modify {
      assertFalse(buildModel === this)
    }
  }

  @Test
  fun testForWriteAppliesModel() {
    setupUpToDate(true)
    val handler = ProjectBuildModelHandlerImpl(myProject, projectBuildModel)
    `when`(projectBuildModel.applyChanges()).then {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
    }
    handler.modify { }
    verify(projectBuildModel).applyChanges()
  }
}