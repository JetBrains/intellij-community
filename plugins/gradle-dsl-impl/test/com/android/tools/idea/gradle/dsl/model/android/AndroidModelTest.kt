/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_BLOCK_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_BLOCK_STATEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_BUILD_TYPE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_SOURCE_SET_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_REMOVE_BUILD_TYPE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_REMOVE_PRODUCT_FLAVOR_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_RESET_BUILD_TYPE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_RESET_DEFAULT_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_RESET_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_AND_RESET_PRODUCT_FLAVOR_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_ARGUMENT
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_APPLICATION_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_ASSIGNMENT_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS_WITH_PARENTHESES
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_ASSIGNMENT_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_BUILD_TYPE_BLOCKS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_DEFAULT_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_EXTERNAL_NATIVE_BUILD_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_NO_DIMENSIONS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_NO_DIMENSIONS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_OVERRIDE_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_ANDROID_BLOCK_WITH_PRODUCT_FLAVOR_BLOCKS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_EDIT_AND_RESET_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_PARSE_NO_RESCONFIGS_PROPERTY
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_BLOCK_APPLICATION_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_BUILD_TYPE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_DEFAULT_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_SOURCE_SET_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_RESET_BUILD_TYPE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_RESET_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_AND_RESET_PRODUCT_FLAVOR_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REMOVE_FROM_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.ANDROID_MODEL_REPLACE_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [AndroidModelImpl].
 */
class AndroidModelTest : GradleFileModelTestCase() {

  private fun runBasicAndroidBlockTest(buildFile: TestFileName) {
    writeToBuildFile(buildFile)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    // Make sure adding to the list works.
    android.flavorDimensions().addListValue().setValue("strawberry")

    applyChangesAndReparse(buildModel)
    // Check that we can get the new parsed value
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version", "strawberry"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }

  @Test
  fun testAndroidBlockWithApplicationStatements() {
    runBasicAndroidBlockTest(ANDROID_MODEL_ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS)
  }

  @Test
  fun testAndroidBlockWithApplicationStatementsWithParentheses() {
    runBasicAndroidBlockTest(ANDROID_MODEL_ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS_WITH_PARENTHESES)
  }

  @Test
  fun testAndroidBlockWithAssignmentStatements() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_ASSIGNMENT_STATEMENTS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion())
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
  }

  @Test
  fun testAndroidApplicationStatements() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_APPLICATION_STATEMENTS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }

  @Test
  fun testAndroidAssignmentStatements() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_ASSIGNMENT_STATEMENTS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
  }

  @Test
  fun testAndroidBlockWithOverrideStatements() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_OVERRIDE_STATEMENTS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "21.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-21", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("dynamicFeatures", listOf(":g1", ":g2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi1", "version1"), android.flavorDimensions())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }

  @Test
  fun testAndroidBlockWithDefaultConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_DEFAULT_CONFIG_BLOCK)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
  }

  @Test
  fun testAndroidBlockWithBuildTypeBlocks() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_BUILD_TYPE_BLOCKS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    val buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(2)
    val buildType1 = buildTypes[0]
    assertEquals("name", "type1", buildType1.name())
    assertEquals("applicationIdSuffix", "typeSuffix-1", buildType1.applicationIdSuffix())
    val buildType2 = buildTypes[1]
    assertEquals("name", "type2", buildType2.name())
    assertEquals("applicationIdSuffix", "typeSuffix-2", buildType2.applicationIdSuffix())
  }

  @Test
  fun testAndroidBlockWithNoDimensions() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_NO_DIMENSIONS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    android.flavorDimensions().addListValue().setValue("strawberry")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ANDROID_BLOCK_WITH_NO_DIMENSIONS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("strawberry"), android.flavorDimensions())
  }

  @Test
  fun testAndroidBlockWithProductFlavorBlocks() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_PRODUCT_FLAVOR_BLOCKS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    val productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(2)
    val flavor1 = productFlavors[0]
    assertEquals("name", "flavor1", flavor1.name())
    assertEquals("applicationId", "com.example.myapplication.flavor1", flavor1.applicationId())
    val flavor2 = productFlavors[1]
    assertEquals("name", "flavor2", flavor2.name())
    assertEquals("applicationId", "com.example.myapplication.flavor2", flavor2.applicationId())
  }

  @Test
  fun testAndroidBlockWithExternalNativeBuildBlock() {
    writeToBuildFile(ANDROID_MODEL_ANDROID_BLOCK_WITH_EXTERNAL_NATIVE_BUILD_BLOCK)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    val externalNativeBuild = android.externalNativeBuild()
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl::class.java)
    val cmake = externalNativeBuild.cmake()
    checkForValidPsiElement(cmake, CMakeModelImpl::class.java)
    assertEquals("path", "foo/bar", cmake.path())
  }

  @Test
  fun testRemoveAndResetElements() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_RESET_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().delete()
    android.compileSdkVersion().delete()
    android.defaultPublishConfig().delete()
    android.dynamicFeatures().delete()
    android.flavorDimensions().delete()
    android.generatePureSplits().delete()
    android.publishNonDefault().delete()
    android.resourcePrefix().delete()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }

  @Test
  fun testEditAndResetLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_EDIT_AND_RESET_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    // Test the fields that also accept an integer value along with the String valye.
    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
  }

  @Test
  fun testAddAndResetLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_RESET_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.resetState()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    // Test the fields that also accept an integer value along with the String value.
    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.resetState()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
  }

  @Test
  fun testReplaceAndResetListElements() {
    writeToBuildFile(ANDROID_MODEL_REPLACE_AND_RESET_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.flavorDimensions().getListValue("abi")!!.setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    android.dynamicFeatures().getListValue(":f2")!!.setValue(":g2")
    assertEquals("dynamicFeatures", listOf(":f1", ":g2"), android.dynamicFeatures())

    buildModel.resetState()
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }

  @Test
  fun testAddAndResetListElements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_RESET_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("xyz")
    android.dynamicFeatures().addListValue().setValue(":f")
    assertEquals("dynamicFeatures", listOf(":f"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    buildModel.resetState()
    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
  }

  @Test
  fun testAddToAndResetListElementsWithArgument() {
    writeToBuildFile(ANDROID_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_ARGUMENT)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("version")
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())
  }

  @Test
  fun testAddToAndResetListElementsWithMultipleArguments() {
    writeToBuildFile(ANDROID_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.dynamicFeatures().addListValue().setValue(":f")
    assertEquals("dynamicFeatures", listOf(":f1", ":f2", ":f"), android.dynamicFeatures())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }

  @Test
  fun testRemoveFromAndResetListElements() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_FROM_AND_RESET_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.dynamicFeatures().getListValue(":f1")!!.delete()
    assertEquals("dynamicFeatures", listOf(":f2"), android.dynamicFeatures())

    android.flavorDimensions().getListValue("version")!!.delete()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }

  @Test
  fun testAddAndResetDefaultConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_RESET_DEFAULT_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty(android.defaultConfig().applicationId())

    android.defaultConfig().applicationId().setValue("foo.bar")
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    buildModel.resetState()
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty(android.defaultConfig().applicationId())
  }

  @Test
  fun testAddAndResetBuildTypeBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_RESET_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertThat(android.buildTypes()).isEmpty()

    android.addBuildType("type")
    val buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type", buildTypes[0].name())

    buildModel.resetState()
    assertThat(android.buildTypes()).isEmpty()
  }

  @Test
  fun testAddAndResetProductFlavorBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_RESET_PRODUCT_FLAVOR_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertThat(android.productFlavors()).isEmpty()

    android.addProductFlavor("flavor")
    val productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor", productFlavors[0].name())

    buildModel.resetState()
    assertThat(android.productFlavors()).isEmpty()
  }

  @Test
  fun testRemoveAndResetBuildTypeBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_RESET_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    var buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())

    android.removeBuildType("type1")
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    buildModel.resetState()
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())
  }

  @Test
  fun testRemoveAndResetProductFlavorBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_RESET_PRODUCT_FLAVOR_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    var productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())

    android.removeProductFlavor("flavor2")
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    buildModel.resetState()
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())
  }

  @Test
  fun testRemoveAndApplyElements() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().delete()
    android.compileSdkVersion().delete()
    android.defaultPublishConfig().delete()
    android.dynamicFeatures().delete()
    android.flavorDimensions().delete()
    android.generatePureSplits().delete()
    android.publishNonDefault().delete()
    android.resourcePrefix().delete()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    applyChanges(buildModel)
    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    checkForInValidPsiElement(android, AndroidModelImpl::class.java)
  }

  @Test
  fun testAddAndRemoveBuildTypeBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_REMOVE_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addBuildType("type")
    val buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildTypes[0].applicationIdSuffix().setValue("suffix")
    assertEquals("buildTypes", "type", buildTypes[0].name())

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).contains("buildTypes")

    android = buildModel.android()
    assertThat(android.buildTypes()).hasSize(1)
    android.removeBuildType("type")
    assertThat(android.buildTypes()).isEmpty()

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).doesNotContain("buildTypes")
  }

  @Test
  fun testAddAndRemoveProductFlavorBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_REMOVE_PRODUCT_FLAVOR_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addProductFlavor("flavor")
    val productFlavors = android.productFlavors()
    productFlavors[0].applicationId().setValue("appId")
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor", productFlavors[0].name())

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).contains("productFlavors")

    android = buildModel.android()
    assertThat(android.productFlavors()).hasSize(1)
    android.removeProductFlavor("flavor")
    assertThat(android.productFlavors()).isEmpty()

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).doesNotContain("productFlavors")
  }

  @Test
  fun testAddAndApplyEmptySigningConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSigningConfig("config")
    val signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config", signingConfigs[0].name())

    applyChanges(buildModel)
    // TODO(xof): empty blocks, as the comments below say, are not saved to the file.  Arguably this is wrong for Kotlin, which
    //  requires explicit creation of signingConfigs (and sourceSets, below)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK)

    assertThat(android.signingConfigs()).isEmpty() // Empty blocks are not saved to the file.

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android.signingConfigs()).isEmpty() // Empty blocks are not saved to the file.
  }

  @Test
  fun testAddAndApplyEmptySourceSetBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSourceSet("set")
    val sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set", sourceSets[0].name())

    applyChanges(buildModel)
    // TODO(xof): see comment in testAddAndApplyEmptySigningConfigBlock
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)

    assertThat(android.sourceSets()).isEmpty() // Empty blocks are not saved to the file.

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android.sourceSets()).isEmpty() // Empty blocks are not saved to the file.
  }

  @Test
  fun testAddAndApplyDefaultConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.defaultConfig().applicationId().setValue("foo.bar")
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK_EXPECTED)

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())
  }

  @Test
  fun testAddAndApplyBuildTypeBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addBuildType("type")
    var buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    var buildType = buildTypes[0]
    buildType.applicationIdSuffix().setValue("mySuffix")

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED)

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())
  }

  @Test
  fun testAddAndApplyProductFlavorBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addProductFlavor("flavor")
    var productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    var productFlavor = productFlavors[0]
    productFlavor.applicationId().setValue("abc.xyz")

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED)

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())
  }

  @Test
  fun testAddAndApplySigningConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSigningConfig("config")
    var signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    var signingConfig = signingConfigs[0]
    signingConfig.keyAlias().setValue("myKeyAlias")

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED)

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())
  }

  @Test
  fun testAddAndApplySourceSetBlock() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSourceSet("set")
    var sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    sourceSet.root().setValue("source")

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())
  }

  @Test
  fun testRemoveAndApplyDefaultConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_DEFAULT_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    android.defaultConfig().applicationId().delete()
    assertMissingProperty(android.defaultConfig().applicationId())
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, "")

    assertMissingProperty(android.defaultConfig().applicationId())
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty(android.defaultConfig().applicationId())
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
  }

  @Test
  fun testRemoveAndApplyBuildTypeBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())

    android.removeBuildType("type1")
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REMOVE_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED)

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())
  }

  @Test
  fun testRemoveAndApplyProductFlavorBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())

    android.removeProductFlavor("flavor2")
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED)

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
  }

  @Test
  fun testRemoveAndApplySigningConfigBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(2)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())
    assertEquals("signingConfigs", "config2", signingConfigs[1].name())

    android.removeSigningConfig("config2")
    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED)

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())
  }

  @Test
  fun testRemoveAndApplySourceSetBlock() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(2)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
    assertEquals("sourceSets", "set2", sourceSets[1].name())

    android.removeSourceSet("set2")
    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
  }

  @Test
  fun testRemoveAndApplyBlockApplicationStatements() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_AND_APPLY_BLOCK_APPLICATION_STATEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())

    defaultConfig.applicationId().delete()
    defaultConfig.proguardFiles().delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertMissingProperty(defaultConfig.applicationId())
    assertMissingProperty(defaultConfig.proguardFiles())
  }

  @Test
  fun testAddAndApplyBlockStatements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_BLOCK_STATEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())

    defaultConfig.dimension().setValue("abcd")
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_BLOCK_STATEMENTS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
  }

  @Test
  fun testEditAndApplyLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())


    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }

  @Test
  fun testEditAndApplyIntegerLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())

    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())
  }

  @Test
  fun testAddAndApplyLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }

  @Test
  fun testAddAndApplyIntegerLiteralElements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())

    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    applyChanges(buildModel)
    // TODO(b/143196166): blocking Kotlinscript version
    // TODO(b/143196529): blocking Kotlinscript version
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())
  }

  @Test
  fun testReplaceAndApplyListElements() {
    writeToBuildFile(ANDROID_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.dynamicFeatures().getListValue(":f2")!!.setValue(":g2")
    assertEquals("dynamicFeatures", listOf(":f1", ":g2"), android.dynamicFeatures())

    android.flavorDimensions().getListValue("abi")!!.setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("dynamicFeatures", listOf(":f1", ":g2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("dynamicFeatures", listOf(":f1", ":g2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())
  }

  @Test
  fun testAddAndApplyListElements() {
    writeToBuildFile(ANDROID_MODEL_ADD_AND_APPLY_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("dynamicFeatures", android.dynamicFeatures())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())

    android.dynamicFeatures().addListValue().setValue(":f")
    assertEquals("dynamicFeatures", listOf(":f"), android.dynamicFeatures())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("dynamicFeatures", listOf(":f"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("dynamicFeatures", listOf(":f"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())
  }

  @Test
  fun testAddToAndApplyListElementsWithOneArgument() {
    writeToBuildFile(ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("version")
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT_EXPECTED)

    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }

  @Test
  fun testAddToAndApplyListElementsWithMultipleArguments() {
    writeToBuildFile(ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.dynamicFeatures().addListValue().setValue(":f")
    assertEquals("dynamicFeatures", listOf(":f1", ":f2", ":f"), android.dynamicFeatures())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS_EXPECTED)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2", ":f"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("dynamicFeatures", listOf(":f1", ":f2", ":f"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())
  }

  @Test
  fun testRemoveFromAndApplyListElements() {
    writeToBuildFile(ANDROID_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("dynamicFeatures", listOf(":f1", ":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    android.dynamicFeatures().getListValue(":f1")!!.delete()
    assertEquals("dynamicFeatures", listOf(":f2"), android.dynamicFeatures())

    android.flavorDimensions().getListValue("version")!!.delete()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, ANDROID_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("dynamicFeatures", listOf(":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("dynamicFeatures", listOf(":f2"), android.dynamicFeatures())
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())
  }

  @Test
  fun testParseNoResConfigsProperty() {
    writeToBuildFile(ANDROID_MODEL_PARSE_NO_RESCONFIGS_PROPERTY)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    var resConfigsModel : GradlePropertyModel?
    var zzzModel : GradlePropertyModel?

    resConfigsModel = buildModel.declaredProperties[0]
    zzzModel = buildModel.declaredProperties[1]

    verifyListProperty(resConfigsModel, listOf("abc"), VARIABLE, 0, "resConfigs")
    verifyListProperty(zzzModel, listOf("def"), VARIABLE, 0, "zzz")

    resConfigsModel = android.defaultConfig().declaredProperties[0]
    zzzModel = android.defaultConfig().declaredProperties[1]

    // TODO(b/143934194): The effect of assigning to a global variable is currently to create a local property shadowing the global.
    //  This is right for the remainder of the scope of the local block (in this case defaultConfig) and technically incorrect for
    //  the rest of the Dsl, though Dsl configuration which depends on order of execution is probably not well-formed.
    verifyListProperty(zzzModel, listOf("jkl"), REGULAR, 0, "zzz")
    verifyListProperty(resConfigsModel, listOf("ghi"), REGULAR, 0, "resConfigs")

    assertMissingProperty(android.defaultConfig().resConfigs())
  }
}
