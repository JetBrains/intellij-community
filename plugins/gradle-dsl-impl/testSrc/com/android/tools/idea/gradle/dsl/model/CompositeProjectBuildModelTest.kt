/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_PROJECT_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_PROJECT_ROOT_BUILD
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_PROJECT_SETTINGS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_PROJECT_SUB_MODULE_BUILD
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CompositeProjectBuildModelTest : GradleFileModelTestCase() {
  private lateinit var compositeRoot: VirtualFile
  private lateinit var compositeSub: VirtualFile

  @Before
  fun prepare() {
    writeToBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD)
    writeToNewProjectFile("applied", COMPOSITE_BUILD_MAIN_PROJECT_APPLIED)
    writeToSubModuleBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD)
    writeToSettingsFile(COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS)

    // Set up the composite project.
    runWriteAction<Unit, IOException> {
      compositeRoot = myProjectBasePath.createChildDirectory(this, "CompositeBuild")
      assertTrue(compositeRoot.exists())
      createFileAndWriteContent(compositeRoot.createChildData(this, "settings$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_PROJECT_SETTINGS)
      createFileAndWriteContent(compositeRoot.createChildData(this, "build$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_PROJECT_ROOT_BUILD)
      createFileAndWriteContent(compositeRoot.createChildData(this, "subApplied$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_PROJECT_APPLIED)
      compositeSub = compositeRoot.createChildDirectory(this, "app")
      assertTrue(compositeSub.exists())
      createFileAndWriteContent(compositeSub.createChildData(this, "build$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_PROJECT_SUB_MODULE_BUILD)
    }
  }

  @Test
  fun testEnsureCompositeBuildProjectDoNotLeakProperties() {
    // Create both ProjectBuildModels
    val mainModel = projectBuildModel
    val compositeModel = getIncludedProjectBuildModel(compositeRoot.path)
    assertNotNull(compositeModel)

    // Check both models contain properties from the applied file
    fun checkBuildscriptDeps(model: ProjectBuildModel) {
      val buildDeps = model.projectBuildModel!!.buildscript().dependencies().artifacts()
      assertSize(2, buildDeps)
      assertEquals("com.android.tools.build:gradle:3.4.0-dev", buildDeps[0].completeModel().forceString())
      assertEquals("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.40", buildDeps[1].completeModel().forceString())
    }
    checkBuildscriptDeps(mainModel)
    checkBuildscriptDeps(compositeModel!!)

    // Check that the ext properties are only visible from the correct build models
    val mainProp = mainModel.projectBuildModel!!.ext().findProperty("mainProjectProperty")
    verifyPropertyModel(mainProp, "ext.mainProjectProperty", "false")
    val compositeProp = compositeModel.projectBuildModel!!.ext().findProperty("compositeProjectProperty")
    verifyPropertyModel(compositeProp, "ext.compositeProjectProperty", "true", compositeRoot.findChild("build$myTestDataExtension")!!.path)
    val wrongMainProp = mainModel.projectBuildModel!!.ext().findProperty("compositeProjectProperty")
    assertMissingProperty(wrongMainProp)
    val wrongCompositeProp = compositeModel.projectBuildModel!!.ext().findProperty("mainProjectProperty")
    assertMissingProperty(wrongCompositeProp)

    // Check applied property in composite subModule
    val appName = compositeModel.getModuleBuildModel(compositeSub.findChild("build$myTestDataExtension")!!).android().defaultConfig().applicationId()
    assertEquals("applicationId", "Super cool app", appName)
  }

  @Test
  fun testEnsureProjectBuildModelsProduceAllBuildModels() {
    val mainModel = projectBuildModel
    val compositeModel = getIncludedProjectBuildModel(compositeRoot.path)

    val mainBuildModels = mainModel.allIncludedBuildModels
    assertSize(2, mainBuildModels)

    val compositeBuildModels = compositeModel!!.allIncludedBuildModels
    assertSize(2, compositeBuildModels)
  }

  private fun createFileAndWriteContent(file: VirtualFile, content: TestFileName) {
    assertTrue(file.exists())
    prepareAndInjectInformationForTest(content, file)
  }
}
