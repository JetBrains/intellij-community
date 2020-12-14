/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.android.BuildFeaturesModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.junit.Test

/**
 * Tests for [BuildFeaturesModel].
 */
class BuildFeaturesModelTest : GradleFileModelTestCase() {
  @Test
  fun testParseElements() {
    writeToBuildFile(TestFileName.BUILD_FEATURES_MODEL_PARSE_ELEMENTS)

    val buildModel = gradleBuildModel
    val buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
  }

  @Test
  fun testEditElements() {
    writeToBuildFile(TestFileName.BUILD_FEATURES_MODEL_EDIT_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
    buildFeatures.compose().setValue(true)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.BUILD_FEATURES_MODEL_EDIT_ELEMENTS_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", true, buildFeatures.compose())
  }

  @Test
  fun testAddElements() {
    writeToBuildFile(TestFileName.BUILD_FEATURES_MODEL_ADD_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertMissingProperty("compose", buildFeatures.compose())
    buildFeatures.compose().setValue(false)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.BUILD_FEATURES_MODEL_ADD_ELEMENTS_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
  }

  @Test
  fun testAddElementsFromExisting() {
    writeToBuildFile(TestFileName.BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertMissingProperty("compose", buildFeatures.compose())
    buildFeatures.compose().setValue(false)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
  }

  @Test
  fun testRemoveElements() {
    writeToBuildFile(TestFileName.BUILD_FEATURES_MODEL_REMOVE_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    checkForValidPsiElement(buildFeatures, BuildFeaturesModelImpl::class.java)
    assertEquals("compose", false, buildFeatures.compose())
    buildFeatures.compose().delete()
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")
    buildFeatures = buildModel.android().buildFeatures()
    checkForInValidPsiElement(buildFeatures, BuildFeaturesModelImpl::class.java)
    assertMissingProperty("compose", buildFeatures.compose())
  }
}