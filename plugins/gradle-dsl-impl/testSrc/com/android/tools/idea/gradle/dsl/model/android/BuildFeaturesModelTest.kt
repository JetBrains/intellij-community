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

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_ADD_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_ADD_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_EDIT_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_EDIT_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_PARSE_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_FEATURES_MODEL_REMOVE_ELEMENTS
import com.android.tools.idea.gradle.dsl.api.android.BuildFeaturesModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.junit.Test

/**
 * Tests for [BuildFeaturesModel].
 */
class BuildFeaturesModelTest : GradleFileModelTestCase() {
  @Test
  fun testParseElements() {
    writeToBuildFile(BUILD_FEATURES_MODEL_PARSE_ELEMENTS)

    val buildModel = gradleBuildModel
    val buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
    assertEquals("mlModelBinding", false, buildFeatures.mlModelBinding())
  }

  @Test
  fun testEditElements() {
    writeToBuildFile(BUILD_FEATURES_MODEL_EDIT_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
    assertEquals("mlModelBinding", false, buildFeatures.mlModelBinding())
    buildFeatures.compose().setValue(true)
    buildFeatures.mlModelBinding().setValue(true)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, BUILD_FEATURES_MODEL_EDIT_ELEMENTS_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", true, buildFeatures.compose())
    assertEquals("mlModelBinding", true, buildFeatures.mlModelBinding())
  }

  @Test
  fun testAddElements() {
    writeToBuildFile(BUILD_FEATURES_MODEL_ADD_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertMissingProperty("compose", buildFeatures.compose())
    assertMissingProperty("mlModelBinding", buildFeatures.mlModelBinding())
    buildFeatures.compose().setValue(false)
    buildFeatures.mlModelBinding().setValue(false)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, BUILD_FEATURES_MODEL_ADD_ELEMENTS_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
    assertEquals("mlModelBinding", false, buildFeatures.mlModelBinding())
  }

  @Test
  fun testAddElementsFromExisting() {
    writeToBuildFile(BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    assertMissingProperty("compose", buildFeatures.compose())
    assertMissingProperty("mlModelBinding", buildFeatures.mlModelBinding())
    buildFeatures.compose().setValue(false)
    buildFeatures.mlModelBinding().setValue(false)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, BUILD_FEATURES_MODEL_ADD_ELEMENTS_FROM_EXISTING_EXPECTED)
    buildFeatures = buildModel.android().buildFeatures()
    assertEquals("compose", false, buildFeatures.compose())
    assertEquals("mlModelBinding", false, buildFeatures.mlModelBinding())
  }

  @Test
  fun testRemoveElements() {
    writeToBuildFile(BUILD_FEATURES_MODEL_REMOVE_ELEMENTS)

    val buildModel = gradleBuildModel
    var buildFeatures = buildModel.android().buildFeatures()
    checkForValidPsiElement(buildFeatures, BuildFeaturesModelImpl::class.java)
    assertEquals("compose", false, buildFeatures.compose())
    assertEquals("mlModelBinding", false, buildFeatures.mlModelBinding())
    buildFeatures.compose().delete()
    buildFeatures.mlModelBinding().delete()
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")
    buildFeatures = buildModel.android().buildFeatures()
    checkForInValidPsiElement(buildFeatures, BuildFeaturesModelImpl::class.java)
    assertMissingProperty("compose", buildFeatures.compose())
    assertMissingProperty("mlModelBinding", buildFeatures.mlModelBinding())
  }
}