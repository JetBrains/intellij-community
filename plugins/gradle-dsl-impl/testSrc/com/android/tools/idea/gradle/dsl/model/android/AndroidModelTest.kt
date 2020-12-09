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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl
import com.google.common.truth.Truth.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

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
    runBasicAndroidBlockTest(TestFile.ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS)
  }

  @Test
  fun testAndroidBlockWithApplicationStatementsWithParentheses() {
    isIrrelevantForKotlinScript("no distinction between method calls and application statements")
    runBasicAndroidBlockTest(TestFile.ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS_WITH_PARENTHESES)
  }

  @Test
  fun testAndroidBlockWithAssignmentStatements() {
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_ASSIGNMENT_STATEMENTS)
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
    writeToBuildFile(TestFile.ANDROID_APPLICATION_STATEMENTS)
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
    writeToBuildFile(TestFile.ANDROID_ASSIGNMENT_STATEMENTS)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
  }

  @Test
  fun testAndroidBlockWithOverrideStatements() {
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_OVERRIDE_STATEMENTS)
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
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_DEFAULT_CONFIG_BLOCK)
    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
  }

  @Test
  fun testAndroidBlockWithBuildTypeBlocks() {
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_BUILD_TYPE_BLOCKS)
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
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_NO_DIMENSIONS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    android.flavorDimensions().addListValue().setValue("strawberry")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ANDROID_BLOCK_WITH_NO_DIMENSIONS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("strawberry"), android.flavorDimensions())
  }

  @Test
  fun testAndroidBlockWithProductFlavorBlocks() {
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_PRODUCT_FLAVOR_BLOCKS)
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
    writeToBuildFile(TestFile.ANDROID_BLOCK_WITH_EXTERNAL_NATIVE_BUILD_BLOCK)
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
    writeToBuildFile(TestFile.REMOVE_AND_RESET_ELEMENTS)
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
    writeToBuildFile(TestFile.EDIT_AND_RESET_LITERAL_ELEMENTS)
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
    writeToBuildFile(TestFile.ADD_AND_RESET_LITERAL_ELEMENTS)
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
    writeToBuildFile(TestFile.REPLACE_AND_RESET_LIST_ELEMENTS)
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
    writeToBuildFile(TestFile.ADD_AND_RESET_LIST_ELEMENTS)
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
    writeToBuildFile(TestFile.ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_ARGUMENT)
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
    writeToBuildFile(TestFile.ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS)
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
    writeToBuildFile(TestFile.REMOVE_FROM_AND_RESET_LIST_ELEMENTS)
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
    writeToBuildFile(TestFile.ADD_AND_RESET_DEFAULT_CONFIG_BLOCK)
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
    writeToBuildFile(TestFile.ADD_AND_RESET_BUILD_TYPE_BLOCK)
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
    writeToBuildFile(TestFile.ADD_AND_RESET_PRODUCT_FLAVOR_BLOCK)
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
    writeToBuildFile(TestFile.REMOVE_AND_RESET_BUILD_TYPE_BLOCK)
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
    writeToBuildFile(TestFile.REMOVE_AND_RESET_PRODUCT_FLAVOR_BLOCK)
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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_ELEMENTS)
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
    writeToBuildFile(TestFile.ADD_AND_REMOVE_BUILD_TYPE_BLOCK)
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
    writeToBuildFile(TestFile.ADD_AND_REMOVE_PRODUCT_FLAVOR_BLOCK)
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
    writeToBuildFile(TestFile.ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSigningConfig("config")
    val signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config", signingConfigs[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK_EXPECTED)

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android.signingConfigs()).hasSize(1)
    assertEquals("signingConfigs", "config", android.signingConfigs()[0].name())
  }

  @Test
  fun testAddAndApplyEmptySourceSetBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.addSourceSet("set")
    val sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set", sourceSets[0].name())

    applyChanges(buildModel)
    // TODO(xof): empty blocks, as the comments below say, are not saved to the file.  Arguably this is wrong for Kotlin, which
    //  requires explicit creation of sourceSets
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)

    assertThat(android.sourceSets()).isEmpty() // Empty blocks are not saved to the file.

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android.sourceSets()).isEmpty() // Empty blocks are not saved to the file.
  }

  @Test
  fun testAddAndApplyDefaultConfigBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android.defaultConfig().applicationId().setValue("foo.bar")
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK_EXPECTED)

    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())
  }

  @Test
  fun testParseVariedSyntaxBuildTypeBlocks() {
    writeToBuildFile(TestFile.PARSE_VARIED_SYNTAX_BUILD_TYPE_BLOCKS)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val buildTypes = android.buildTypes()
    assertSize(6, buildTypes)
    assertEquals("one", buildTypes[0].name())
    assertEquals("two", buildTypes[1].name())
    assertEquals("three", buildTypes[2].name())
    assertEquals("four", buildTypes[3].name())
    assertEquals("five", buildTypes[4].name())
    assertEquals("six", buildTypes[5].name())
  }

  private fun doTestAddAndApplyOneBuildTypeBlock(name : String, expected : TestFileName) {
    writeToBuildFile(TestFile.ADD_AND_APPLY_BUILD_TYPE_BLOCK)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    android.addBuildType(name)
    var buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", name, buildTypes[0].name())
    buildTypes[0].applicationIdSuffix().setValue("foo")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, expected)

    buildTypes = gradleBuildModel.android().buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", name, buildTypes[0].name())
    assertEquals("applicationIdSuffix", "foo", buildTypes[0].applicationIdSuffix())
  }

  @Test
  fun testAddAndApplyDottedBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("dotted.buildtype", TestFile.ADD_AND_APPLY_DOTTED_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyOperatorBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("debug-custom", TestFile.ADD_AND_APPLY_OPERATOR_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyDerefBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("debug[0]", TestFile.ADD_AND_APPLY_DEREF_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplySpaceBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("space buildtype", TestFile.ADD_AND_APPLY_SPACE_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyNumericBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("2abc", TestFile.ADD_AND_APPLY_NUMERIC_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyNonAsciiBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("ħƁǅẅΣЖא", TestFile.ADD_AND_APPLY_NON_ASCII_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyLanguageKeywordBuildTypeBlock() {
    doTestAddAndApplyOneBuildTypeBlock("class", TestFile.ADD_AND_APPLY_LANGUAGE_KEYWORD_BUILD_TYPE_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyBuildTypeBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_BUILD_TYPE_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_AND_APPLY_SIGNING_CONFIG_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_AND_APPLY_SOURCE_SET_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_DEFAULT_CONFIG_BLOCK)
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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_BUILD_TYPE_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_SOURCE_SET_BLOCK)
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_BLOCK_APPLICATION_STATEMENTS)
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
    writeToBuildFile(TestFile.ADD_AND_APPLY_BLOCK_STATEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_BLOCK_STATEMENTS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
  }

  @Test
  fun testEditAndApplyLiteralElements() {
    writeToBuildFile(TestFile.EDIT_AND_APPLY_LITERAL_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_AND_APPLY_LITERAL_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

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
    assumeTrue("can't assign an int to compileSdkVersion in KotlinScript", !isKotlinScript) // TODO(b/143196166), TODO(b/143196529)
    writeToBuildFile(TestFile.ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.REPLACE_AND_APPLY_LIST_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_AND_APPLY_LIST_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("version")
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT_EXPECTED)

    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }

  @Test
  fun testAddToAndApplyListElementsWithMultipleArguments() {
    writeToBuildFile(TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS)
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
    verifyFileContents(myBuildFile, TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS)
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED)

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
    writeToBuildFile(TestFile.PARSE_NO_RESCONFIGS_PROPERTY)

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

  enum class TestFile(val path: @SystemDependent String): TestFileName {
    ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS("androidBlockWithApplicationStatements"),
    ANDROID_BLOCK_WITH_APPLICATION_STATEMENTS_WITH_PARENTHESES("androidBlockWithApplicationStatementsWithParentheses"),
    ANDROID_BLOCK_WITH_ASSIGNMENT_STATEMENTS("androidBlockWithAssignmentStatements"),
    ANDROID_APPLICATION_STATEMENTS("androidApplicationStatements"),
    ANDROID_ASSIGNMENT_STATEMENTS("androidAssignmentStatements"),
    ANDROID_BLOCK_WITH_OVERRIDE_STATEMENTS("androidBlockWithOverrideStatements"),
    ANDROID_BLOCK_WITH_DEFAULT_CONFIG_BLOCK("androidBlockWithDefaultConfigBlock"),
    ANDROID_BLOCK_WITH_BUILD_TYPE_BLOCKS("androidBlockWithBuildTypeBlocks"),
    ANDROID_BLOCK_WITH_NO_DIMENSIONS("androidBlockWithNoDimensions"),
    ANDROID_BLOCK_WITH_NO_DIMENSIONS_EXPECTED("androidBlockWithNoDimensionsExpected"),
    ANDROID_BLOCK_WITH_PRODUCT_FLAVOR_BLOCKS("androidBlockWithProductFlavorBlocks"),
    ANDROID_BLOCK_WITH_EXTERNAL_NATIVE_BUILD_BLOCK("androidBlockWithExternalNativeBuildBlock"),
    REMOVE_AND_RESET_ELEMENTS("removeAndResetElements"),
    EDIT_AND_RESET_LITERAL_ELEMENTS("editAndResetLiteralElements"),
    ADD_AND_RESET_LITERAL_ELEMENTS("addAndResetLiteralElements"),
    REPLACE_AND_RESET_LIST_ELEMENTS("replaceAndResetListElements"),
    ADD_AND_RESET_LIST_ELEMENTS("addAndResetListElements"),
    ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_ARGUMENT("addToAndResetListElementsWithArgument"),
    ADD_TO_AND_RESET_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS("addToAndResetListElementsWithMultipleArguments"),
    REMOVE_FROM_AND_RESET_LIST_ELEMENTS("removeFromAndResetListElements"),
    ADD_AND_RESET_DEFAULT_CONFIG_BLOCK("addAndResetDefaultConfigBlock"),
    ADD_AND_RESET_BUILD_TYPE_BLOCK("addAndResetBuildTypeBlock"),
    ADD_AND_RESET_PRODUCT_FLAVOR_BLOCK("addAndResetProductFlavorBlock"),
    REMOVE_AND_RESET_BUILD_TYPE_BLOCK("removeAndResetBuildTypeBlock"),
    REMOVE_AND_RESET_PRODUCT_FLAVOR_BLOCK("removeAndResetProductFlavorBlock"),
    REMOVE_AND_APPLY_ELEMENTS("removeAndApplyElements"),
    ADD_AND_REMOVE_BUILD_TYPE_BLOCK("addAndRemoveBuildTypeBlock"),
    ADD_AND_REMOVE_PRODUCT_FLAVOR_BLOCK("addAndRemoveProductFlavorBlock"),
    ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK("addAndApplyEmptySigningConfigBlock"),
    ADD_AND_APPLY_EMPTY_SIGNING_CONFIG_BLOCK_EXPECTED("addAndApplyEmptySigningConfigBlockExpected"),
    ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK("addAndApplyEmptySourceSetBlock"),
    ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK("addAndApplyDefaultConfigBlock"),
    ADD_AND_APPLY_DEFAULT_CONFIG_BLOCK_EXPECTED("addAndApplyDefaultConfigBlockExpected"),
    PARSE_VARIED_SYNTAX_BUILD_TYPE_BLOCKS("parseVariedSyntaxBuildTypeBlocks"),
    ADD_AND_APPLY_BUILD_TYPE_BLOCK("addAndApplyBuildTypeBlock"),
    ADD_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyBuildTypeBlockExpected"),
    ADD_AND_APPLY_DEREF_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyDerefBuildTypeBlockExpected"),
    ADD_AND_APPLY_DOTTED_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyDottedBuildTypeBlockExpected"),
    ADD_AND_APPLY_LANGUAGE_KEYWORD_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyLanguageKeywordBuildTypeBlockExpected"),
    ADD_AND_APPLY_NON_ASCII_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyNonAsciiBuildTypeBlockExpected"),
    ADD_AND_APPLY_NUMERIC_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyNumericBuildTypeBlockExpected"),
    ADD_AND_APPLY_OPERATOR_BUILD_TYPE_BLOCK_EXPECTED("addAndApplyOperatorBuildTypeBlockExpected"),
    ADD_AND_APPLY_SPACE_BUILD_TYPE_BLOCK_EXPECTED("addAndApplySpaceBuildTypeBlockExpected"),
    ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK("addAndApplyProductFlavorBlock"),
    ADD_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED("addAndApplyProductFlavorBlockExpected"),
    ADD_AND_APPLY_SIGNING_CONFIG_BLOCK("addAndApplySigningConfigBlock"),
    ADD_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED("addAndApplySigningConfigBlockExpected"),
    ADD_AND_APPLY_SOURCE_SET_BLOCK("addAndApplySourceSetBlock"),
    ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED("addAndApplySourceSetBlockExpected"),
    REMOVE_AND_APPLY_DEFAULT_CONFIG_BLOCK("removeAndApplyDefaultConfigBlock"),
    REMOVE_AND_APPLY_BUILD_TYPE_BLOCK("removeAndApplyBuildTypeBlock"),
    REMOVE_AND_APPLY_BUILD_TYPE_BLOCK_EXPECTED("removeAndApplyBuildTypeBlockExpected"),
    REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK("removeAndApplyProductFlavorBlock"),
    REMOVE_AND_APPLY_PRODUCT_FLAVOR_BLOCK_EXPECTED("removeAndApplyProductFlavorBlockExpected"),
    REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK("removeAndApplySigningConfigBlock"),
    REMOVE_AND_APPLY_SIGNING_CONFIG_BLOCK_EXPECTED("removeAndApplySigningConfigBlockExpected"),
    REMOVE_AND_APPLY_SOURCE_SET_BLOCK("removeAndApplySourceSetBlock"),
    REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED("removeAndApplySourceSetBlockExpected"),
    REMOVE_AND_APPLY_BLOCK_APPLICATION_STATEMENTS("removeAndApplyBlockApplicationStatements"),
    ADD_AND_APPLY_BLOCK_STATEMENTS("addAndApplyBlockStatements"),
    ADD_AND_APPLY_BLOCK_STATEMENTS_EXPECTED("addAndApplyBlockStatementsExpected"),
    EDIT_AND_APPLY_LITERAL_ELEMENTS("editAndApplyLiteralElements"),
    EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED("editAndApplyLiteralElementsExpected"),
    EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS("editAndApplyIntegerLiteralElements"),
    EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED("editAndApplyIntegerLiteralElementsExpected"),
    ADD_AND_APPLY_LITERAL_ELEMENTS("addAndApplyLiteralElements"),
    ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED("addAndApplyLiteralElementsExpected"),
    ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS("addAndApplyIntegerLiteralElements"),
    ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED("addAndApplyIntegerLiteralElementsExpected"),
    REPLACE_AND_APPLY_LIST_ELEMENTS("replaceAndApplyListElements"),
    REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED("replaceAndApplyListElementsExpected"),
    ADD_AND_APPLY_LIST_ELEMENTS("addAndApplyListElements"),
    ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED("addAndApplyListElementsExpected"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT("addToAndApplyListElementsWithOneArgument"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_ONE_ARGUMENT_EXPECTED("addToAndApplyListElementsWithOneArgumentExpected"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS("addToAndApplyListElementsWithMultipleArguments"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS_WITH_MULTIPLE_ARGUMENTS_EXPECTED("addToAndApplyListElementsWithMultipleArgumentsExpected"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS("removeFromAndApplyListElements"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED("removeFromAndApplyListElementsExpected"),
    PARSE_NO_RESCONFIGS_PROPERTY("parseNoResConfigsProperty"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/androidModel/$path", extension)
    }
  }
}

