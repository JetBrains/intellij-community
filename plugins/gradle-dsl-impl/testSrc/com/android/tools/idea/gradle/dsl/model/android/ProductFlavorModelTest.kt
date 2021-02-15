/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atA
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

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_MISSING_DIMENSION_STRATEGY
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_MISSING_DIMENSION_STRATEGY_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_NATIVE_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_NATIVE_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_APPEND_MATCHING_FALLBACKS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_APPEND_MATCHING_FALLBACKS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_APPLICATION_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_ASSIGNMENT_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_APPEND_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_OVERRIDE_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_MAP_STATEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DELETE_MATCHING_FALLBACKS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_DELETE_MATCHING_FALLBACKS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_AND_RESET_LITERAL_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_EDIT_NATIVE_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_ENSURE_SDK_VERSION_USES_APPLICATION_SYNTAX_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_FUNCTION_CALL_WITH_PARENTHESES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_NATIVE_ELEMENT_TEXT
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_PARSE_MATCHING_FALLBACKS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_MAP_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_AND_RESET_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_AND_RESET_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_EXTERNAL_NATIVE_BLOCK_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_MISSING_DIMENSION_STRATEGY_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_NATIVE_BLOCK_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_ONE_OF_NATIVE_ELEMENTS_IN_THE_LIST_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_ONLY_NATIVE_ELEMENT_IN_THE_LIST
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_RES_CONFIG_IN_LIST_METHOD_CALL
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REMOVE_RES_CONFIG_IN_LIST_METHOD_CALL_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_REPLACE_AND_RESET_LIST_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_RES_CONFIGS_IN_LIST_METHOD_CALL
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_RES_CONFIGS_IN_LIST_METHOD_CALL_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_AND_APPLY_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_AND_APPLY_MAP_ELEMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_AND_RESET_MAP_ELEMENTS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_CLEARS_PROGUARD_FILES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_WITH_REFERENCE
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_WITH_REFERENCE_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_TEST_INSTRUMENTATION_RUNNER_ARGUMENT_PLURAL_THEN_SINGULAR
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_TEST_INSTRUMENTATION_RUNNER_ARGUMENT_SINGULAR_THEN_PLURAL
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVOR_MODEL_WRITE_MATCHING_FALLBACKS_EXPECTED
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.MAP_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.CMakeOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.NdkBuildOptionsModelImpl
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tests for [ProductFlavorModelImpl].
 *
 *
 * Both `android.defaultConfig {}` and `android.productFlavors.xyz {}` uses the same structure with same attributes.
 * In this test, the product flavor structure defined by [ProductFlavorModelImpl] is tested in great deal to cover all combinations using
 * the `android.defaultConfig {}` block. The general structure of `android.productFlavors {}` is tested in
 * [ProductFlavorModelTest].
 */
class ProductFlavorModelTest : GradleFileModelTestCase() {
  @Test
  fun testDefaultConfigBlockWithApplicationStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("multiDexKeepFile", "multidex.keep", defaultConfig.multiDexKeepFile())
    assertEquals("multiDexKeepProguard", "multidex.proguard", defaultConfig.multiDexKeepProguard())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("renderscriptTargetApi", 18, defaultConfig.renderscriptTargetApi())
    assertEquals("renderscriptSupportModeEnabled", true, defaultConfig.renderscriptSupportModeEnabled())
    assertEquals("renderscriptSupportModeBlasEnabled", false, defaultConfig.renderscriptSupportModelBlasEnabled())
    assertEquals("renderscriptNdkModeEnabled", true, defaultConfig.renderscriptNdkModeEnabled())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    val vectorDrawables = defaultConfig.vectorDrawables()
    assertEquals("useSupportLibrary", true, vectorDrawables.useSupportLibrary())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
    assertEquals("wearAppUnbundled", true, defaultConfig.wearAppUnbundled())
  }

  @Test
  fun testDefaultConfigBlockWithAssignmentStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("renderscriptTargetApi", 18, defaultConfig.renderscriptTargetApi())
    assertEquals("renderscriptSupportModeEnabled", true, defaultConfig.renderscriptSupportModeEnabled())
    assertEquals("renderscriptSupportModeBlasEnabled", false, defaultConfig.renderscriptSupportModelBlasEnabled())
    assertEquals("renderscriptNdkModeEnabled", true, defaultConfig.renderscriptNdkModeEnabled())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    val vectorDrawables = defaultConfig.vectorDrawables()
    verifyListProperty(vectorDrawables.generatedDensities(), listOf("yes", "no", "maybe"), true)
    assertEquals("useSupportLibrary", true, vectorDrawables.useSupportLibrary())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
    assertEquals("wearAppUnbundled", true, defaultConfig.wearAppUnbundled())
  }

  @Test
  fun testDefaultConfigApplicationStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_APPLICATION_STATEMENTS)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  @Test
  fun testDefaultConfigAssignmentStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_ASSIGNMENT_STATEMENTS)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  @Test
  fun testDefaultConfigBlockWithOverrideStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_OVERRIDE_STATEMENTS)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication1", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules-1.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel3" to "defaultName3", "activityLabel4" to "defaultName4"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules-1.pro"), defaultConfig.proguardFiles())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test1", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("key" to "value"), defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
    assertEquals("versionName", "3.0", defaultConfig.versionName())
  }

  @Test
  fun testDefaultConfigBlockWithAppendStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_BLOCK_WITH_APPEND_STATEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders",
                 mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2", "activityLabel3" to "defaultName3",
                       "activityLabel4" to "defaultName4"), defaultConfig.manifestPlaceholders())
    assertEquals("proguardFiles", listOf("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "ijkl", "mnop", "qrst"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())
    val expected = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3", "key4" to "value4",
                         "key5" to "value5", "key6" to "value6", "key7" to "value7")
    assertEquals("testInstrumentationRunnerArguments", expected, defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testDefaultConfigMapStatements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DEFAULT_CONFIG_MAP_STATEMENTS)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("key1" to "value1", "key2" to "value2"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testRemoveAndResetElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_AND_RESET_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().delete()
    defaultConfig.consumerProguardFiles().delete()
    defaultConfig.dimension().delete()
    defaultConfig.manifestPlaceholders().delete()
    defaultConfig.maxSdkVersion().delete()
    defaultConfig.minSdkVersion().delete()
    defaultConfig.multiDexEnabled().delete()
    defaultConfig.proguardFiles().delete()
    defaultConfig.resConfigs().delete()
    defaultConfig.removeAllResValues()
    defaultConfig.targetSdkVersion().delete()
    defaultConfig.testApplicationId().delete()
    defaultConfig.testFunctionalTest().delete()
    defaultConfig.testHandleProfiling().delete()
    defaultConfig.testInstrumentationRunner().delete()
    defaultConfig.testInstrumentationRunnerArguments().delete()
    defaultConfig.useJack().delete()
    defaultConfig.versionCode().delete()
    defaultConfig.versionName().delete()

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    buildModel.resetState()

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  @Test
  fun testEditAndResetLiteralElements() {
    assumeTrue("KotlinScript prefers assignment even when setters are more general", !isKotlinScript) // TODO(b/143196166)
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_EDIT_AND_RESET_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.resetState()

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    // Test the fields that also accept an integer value along with the String value.
    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.resetState()

    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
  }

  @Test
  fun testAddAndResetLiteralElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.resetState()

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    // Test the fields that also accept an integer value along with the String valye.
    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.resetState()

    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
  }

  @Test
  fun testReplaceAndResetListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REPLACE_AND_RESET_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    replaceListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.resConfigs(), "abcd", "xyz")
    defaultConfig.replaceResValue("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst")

    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  @Test
  fun testAddAndResetListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android.txt")

    defaultConfig.proguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.resConfigs().addListValue().setValue("abcd")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    buildModel.resetState()

    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())
  }

  @Test
  fun testAddToAndResetListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_TO_AND_RESET_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.resConfigs().addListValue().setValue("xyz")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  @Test
  fun testRemoveFromAndResetListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_RESET_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.resConfigs(), "efgh")
    defaultConfig.removeResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())
  }

  @Test
  fun testSetAndResetMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_SET_AND_RESET_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("key1").setValue(12345)
    defaultConfig.manifestPlaceholders().getMapValue("key3").setValue(true)
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf<String, Any>("key1" to 12345, "key2" to "value2", "key3" to true),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testAddAndResetMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_RESET_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1")
    defaultConfig.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testRemoveAndResetMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_AND_RESET_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getValue(MAP_TYPE)!!["activityLabel1"]!!.delete()
    defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE)!!["size"]!!.delete()

    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testRemoveAndApplyElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    checkForValidPsiElement(android, AndroidModelImpl::class.java)

    val defaultConfig = android.defaultConfig()
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl::class.java)

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().delete()
    defaultConfig.consumerProguardFiles().delete()
    defaultConfig.dimension().delete()
    defaultConfig.dimension().delete()
    defaultConfig.manifestPlaceholders().delete()
    defaultConfig.maxSdkVersion().delete()
    defaultConfig.minSdkVersion().delete()
    defaultConfig.multiDexEnabled().delete()
    defaultConfig.proguardFiles().delete()
    defaultConfig.resConfigs().delete()
    defaultConfig.removeAllResValues()
    defaultConfig.targetSdkVersion().delete()
    defaultConfig.testApplicationId().delete()
    defaultConfig.testFunctionalTest().delete()
    defaultConfig.testHandleProfiling().delete()
    defaultConfig.testInstrumentationRunner().delete()
    defaultConfig.testInstrumentationRunnerArguments().delete()
    defaultConfig.useJack().delete()
    defaultConfig.versionCode().delete()
    defaultConfig.versionName().delete()

    checkForValidPsiElement(android, AndroidModelImpl::class.java)
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl::class.java)

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, "") // the android/defaultConfig block is now empty and will be deleted

    checkForInValidPsiElement(android, AndroidModelImpl::class.java)
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    checkForInValidPsiElement(android, AndroidModelImpl::class.java)
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())
  }

  @Test
  fun testEditAndApplyLiteralElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())
  }

  @Test
  fun testEditAndApplyIntegerLiteralElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 1, defaultConfig.versionCode())

    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_EDIT_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
  }

  @Test
  fun testAddAndApplyLiteralElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue(2)
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())
  }

  @Test
  fun testAddAndApplyIntegerLiteralElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("versionCode", defaultConfig.versionCode())

    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_INTEGER_LITERAL_ELEMENTS_EXPECTED)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
  }

  @Test
  fun testReplaceAndApplyListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    replaceListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.resConfigs(), "abcd", "xyz")
    defaultConfig.replaceResValue("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst")

    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())
  }

  @Test
  fun testAddAndApplyListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertEmpty("resValues", defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-rules.pro")
    defaultConfig.resConfigs().addListValue().setValue("abcd")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())
  }

  @Test
  fun testAddToAndApplyListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.resConfigs().addListValue().setValue("xyz")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_TO_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())
  }

  @Test
  fun testRemoveFromAndApplyListElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
                 defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
                     defaultConfig.resValues())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.resConfigs(), "efgh")
    defaultConfig.removeResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED)

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  @Test
  fun testRemoveFromAndApplyListElementsWithSingleElement() {
    // TODO(b/72853928): see the comment regarding the analogous test in BuildTypeModelTest
    assumeTrue("setProguardFiles parsing/model implementation insufficient in KotlinScript", !isKotlinScript)
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-rules.pro"), defaultConfig.proguardFiles())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")

    assertThat(defaultConfig.consumerProguardFiles().getValue(LIST_TYPE)).named("consumerProguardFiles").isEmpty()
    assertThat(defaultConfig.proguardFiles().getValue(LIST_TYPE)).named("proguardFiles").isEmpty()

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT_EXPECTED)

    assertThat(defaultConfig.consumerProguardFiles().getValue(LIST_TYPE)).named("consumerProguardFiles").isEmpty()
    assertThat(defaultConfig.proguardFiles().getValue(LIST_TYPE)).named("proguardFiles").isEmpty()

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty(android.defaultConfig().consumerProguardFiles())
    assertSize(0, android.defaultConfig().proguardFiles().getValue(LIST_TYPE)!!)
  }

  @Test
  fun testSetAndApplyMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_SET_AND_APPLY_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("key1").setValue(12345)
    defaultConfig.manifestPlaceholders().getMapValue("key3").setValue(true)
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_SET_AND_APPLY_MAP_ELEMENTS_EXPECTED)

    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testAddAndApplyMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    verifyEmptyMapProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1")
    defaultConfig.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED)

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testRemoveAndApplyMapElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_MAP_ELEMENTS)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getValue(MAP_TYPE)!!["activityLabel1"]!!.delete()
    defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE)!!["size"]!!.delete()

    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_AND_APPLY_MAP_ELEMENTS_EXPECTED)

    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
                 defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
                 defaultConfig.testInstrumentationRunnerArguments())
  }

  @Test
  fun testParseNativeElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_NATIVE_ELEMENT_TEXT)
    verifyNativeElements()
  }

  @Test
  fun testEditNativeElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_NATIVE_ELEMENT_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake.abiFilters().getListValue("abiFilter2")!!.setValue("abiFilterX")
    cmake.arguments().getListValue("argument2")!!.setValue("argumentX")
    cmake.cFlags().getListValue("cFlag2")!!.setValue("cFlagX")
    cmake.cppFlags().getListValue("cppFlag2")!!.setValue("cppFlagX")
    cmake.targets().getListValue("target2")!!.setValue("targetX")

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild.abiFilters().getListValue("abiFilter4")!!.setValue("abiFilterY")
    ndkBuild.arguments().getListValue("argument4")!!.setValue("argumentY")
    ndkBuild.cFlags().getListValue("cFlag4")!!.setValue("cFlagY")
    ndkBuild.cppFlags().getListValue("cppFlag4")!!.setValue("cppFlagY")
    ndkBuild.targets().getListValue("target4")!!.setValue("targetY")

    var ndk = defaultConfig.ndk()
    ndk.abiFilters().getListValue("abiFilter6")!!.setValue("abiFilterZ")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_EDIT_NATIVE_ELEMENTS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter1", "abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument1", "argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag1", "cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag1", "cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target1", "targetX"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter3", "abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument3", "argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag3", "cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag3", "cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target3", "targetY"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilterZ", "abiFilter7"), ndk.abiFilters())
  }

  @Test
  fun testAddNativeElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_NATIVE_ELEMENTS)
    verifyNullNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake.abiFilters().addListValue().setValue("abiFilterX")
    cmake.arguments().addListValue().setValue("argumentX")
    cmake.cFlags().addListValue().setValue("cFlagX")
    cmake.cppFlags().addListValue().setValue("cppFlagX")
    cmake.targets().addListValue().setValue("targetX")

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild.abiFilters().addListValue().setValue("abiFilterY")
    ndkBuild.arguments().addListValue().setValue("argumentY")
    ndkBuild.cFlags().addListValue().setValue("cFlagY")
    ndkBuild.cppFlags().addListValue().setValue("cppFlagY")
    ndkBuild.targets().addListValue().setValue("targetY")

    var ndk = defaultConfig.ndk()
    ndk.abiFilters().addListValue().setValue("abiFilterZ")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_NATIVE_ELEMENTS_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("targetX"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("targetY"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilterZ"), ndk.abiFilters())
  }

  @Test
  fun testRemoveNativeElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_NATIVE_ELEMENT_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    cmake.abiFilters().delete()
    cmake.arguments().delete()
    cmake.cFlags().delete()
    cmake.cppFlags().delete()
    cmake.targets().delete()

    val ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild.abiFilters().delete()
    ndkBuild.arguments().delete()
    ndkBuild.cFlags().delete()
    ndkBuild.cppFlags().delete()
    ndkBuild.targets().delete()

    val ndk = defaultConfig.ndk()
    ndk.abiFilters().delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    verifyNullNativeElements()
  }

  @Test
  fun testRemoveOneOfNativeElementsInTheList() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_NATIVE_ELEMENT_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake.abiFilters().getListValue("abiFilter1")!!.delete()
    cmake.arguments().getListValue("argument1")!!.delete()
    cmake.cFlags().getListValue("cFlag1")!!.delete()
    cmake.cppFlags().getListValue("cppFlag1")!!.delete()
    cmake.targets().getListValue("target1")!!.delete()

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild.abiFilters().getListValue("abiFilter3")!!.delete()
    ndkBuild.arguments().getListValue("argument3")!!.delete()
    ndkBuild.cFlags().getListValue("cFlag3")!!.delete()
    ndkBuild.cppFlags().getListValue("cppFlag3")!!.delete()
    ndkBuild.targets().getListValue("target3")!!.delete()

    var ndk = defaultConfig.ndk()
    ndk.abiFilters().getListValue("abiFilter6")!!.delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_ONE_OF_NATIVE_ELEMENTS_IN_THE_LIST_EXPECTED)

    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter2"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument2"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag2"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag2"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target2"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter4"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument4"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag4"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag4"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target4"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilter7"), ndk.abiFilters())
  }

  @Test
  fun testRemoveOnlyNativeElementInTheList() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_ONLY_NATIVE_ELEMENT_IN_THE_LIST)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("targetX"), cmake.targets())

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("targetY"), ndkBuild.targets())

    val ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilterZ"), ndk.abiFilters())

    cmake.abiFilters().getListValue("abiFilterX")!!.delete()
    cmake.arguments().getListValue("argumentX")!!.delete()
    cmake.cFlags().getListValue("cFlagX")!!.delete()
    cmake.cppFlags().getListValue("cppFlagX")!!.delete()
    cmake.targets().getListValue("targetX")!!.delete()

    ndkBuild.abiFilters().getListValue("abiFilterY")!!.delete()
    ndkBuild.arguments().getListValue("argumentY")!!.delete()
    ndkBuild.cFlags().getListValue("cFlagY")!!.delete()
    ndkBuild.cppFlags().getListValue("cppFlagY")!!.delete()
    ndkBuild.targets().getListValue("targetY")!!.delete()

    ndk.abiFilters().getListValue("abiFilterZ")!!.delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    verifyNullNativeElements()
  }

  private fun verifyNativeElements() {
    val android = gradleBuildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter1", "abiFilter2"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument1", "argument2"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag1", "cFlag2"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag1", "cppFlag2"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target1", "target2"), cmake.targets())

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter3", "abiFilter4"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument3", "argument4"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag3", "cFlag4"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag3", "cppFlag4"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target3", "target4"), ndkBuild.targets())

    val ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilter6", "abiFilter7"), ndk.abiFilters())
  }

  private fun verifyNullNativeElements() {
    val android = gradleBuildModel.android()
    assertNotNull(android)
    val defaultConfig = android.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertMissingProperty("cmake-abiFilters", cmake.abiFilters())
    assertMissingProperty("cmake-arguments", cmake.arguments())
    assertMissingProperty("cmake-cFlags", cmake.cFlags())
    assertMissingProperty("cmake-cppFlags", cmake.cppFlags())
    assertMissingProperty("cmake-targets", cmake.targets())
    checkForInValidPsiElement(cmake, CMakeOptionsModelImpl::class.java)

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertMissingProperty("ndkBuild-abiFilters", ndkBuild.abiFilters())
    assertMissingProperty("ndkBuild-arguments", ndkBuild.arguments())
    assertMissingProperty("ndkBuild-cFlags", ndkBuild.cFlags())
    assertMissingProperty("ndkBuild-cppFlags", ndkBuild.cppFlags())
    assertMissingProperty("ndkBuild-targets", ndkBuild.targets())
    checkForInValidPsiElement(ndkBuild, NdkBuildOptionsModelImpl::class.java)

    val ndk = defaultConfig.ndk()
    assertMissingProperty("ndk-abiFilters", ndk.abiFilters())
    checkForInValidPsiElement(ndk, NdkOptionsModelImpl::class.java)
  }

  @Test
  fun testRemoveNativeBlockElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_NATIVE_BLOCK_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    var defaultConfig = android.defaultConfig()
    checkForValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl::class.java)
    checkForValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl::class.java)

    defaultConfig.removeExternalNativeBuild()
    defaultConfig.removeNdk()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android.defaultConfig()
    checkForInValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl::class.java)
    checkForInValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl::class.java)
  }

  @Test
  fun testRemoveExternalNativeBlockElements() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_EXTERNAL_NATIVE_BLOCK_ELEMENTS)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    var externalNativeBuild = android.defaultConfig().externalNativeBuild()
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl::class.java)
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl::class.java)

    externalNativeBuild.removeCMake()
    externalNativeBuild.removeNdkBuild()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    android = buildModel.android()
    assertNotNull(android)
    externalNativeBuild = android.defaultConfig().externalNativeBuild()
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl::class.java)
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl::class.java)
  }

  @Test
  fun testFunctionCallWithParentheses() {
    isIrrelevantForKotlinScript("All function calls in KotlinScript involve parentheses")
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_FUNCTION_CALL_WITH_PARENTHESES)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android.defaultConfig()
    assertEquals("targetSdkVersion", 19, defaultConfig.targetSdkVersion())
  }

  @Test
  fun testEnsureSdkVersionUsesApplicationSyntax() {
    assumeTrue("KotlinScript prefers assignment even when setters are more general", !isKotlinScript) // TODO(b/143196166)
    val text = ""
    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val defaultConfig = buildModel.android().defaultConfig()

    defaultConfig.minSdkVersion().setValue(18)
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.targetSdkVersion().setValue(24)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ENSURE_SDK_VERSION_USES_APPLICATION_SYNTAX_EXPECTED)
  }

  @Test
  fun testParseMatchingFallbacks() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_PARSE_MATCHING_FALLBACKS)
    val buildModel = gradleBuildModel
    val demoFlavour = buildModel.android().productFlavors()[0]!!
    val resolvedPropertyModel = demoFlavour.matchingFallbacks()
    verifyListProperty(resolvedPropertyModel, listOf("trial", "free"))
  }

  @Test
  fun testWriteMatchingFallbacks() {
    //assumeTrue(isGroovy())
    val text = ""
    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val demoFlavour = buildModel.android().addProductFlavor("demo")
    val resolvedPropertyModel = demoFlavour.matchingFallbacks()
    assertMissingProperty(resolvedPropertyModel)
    resolvedPropertyModel.convertToEmptyList().addListValue().setValue("trial")
    resolvedPropertyModel.addListValue().setValue("free")
    verifyListProperty(resolvedPropertyModel, listOf("trial", "free"))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_WRITE_MATCHING_FALLBACKS_EXPECTED)

    verifyListProperty(buildModel.android().productFlavors()[0].matchingFallbacks(), listOf("trial", "free"))
  }

  @Test
  fun testAppendMatchingFallbacks() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_APPEND_MATCHING_FALLBACKS)
    val buildModel = gradleBuildModel
    val demoFlavour = buildModel.android().productFlavors()[0]!!
    val resolvedPropertyModel = demoFlavour.matchingFallbacks()
    verifyListProperty(resolvedPropertyModel, listOf("trial"))

    resolvedPropertyModel.addListValue().setValue("free")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_APPEND_MATCHING_FALLBACKS_EXPECTED)

    verifyListProperty(buildModel.android().productFlavors()[0].matchingFallbacks(), listOf("trial", "free"))
  }

  @Test
  fun testDeleteMatchingFallbacks() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_DELETE_MATCHING_FALLBACKS)
    val buildModel = gradleBuildModel
    val demoFlavour = buildModel.android().productFlavors()[0]!!
    val resolvedPropertyModel = demoFlavour.matchingFallbacks()
    verifyListProperty(resolvedPropertyModel, listOf("trial"))

    resolvedPropertyModel.delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_DELETE_MATCHING_FALLBACKS_EXPECTED)

    assertMissingProperty(buildModel.android().productFlavors()[0].matchingFallbacks())
  }

  @Test
  fun testMissingDimensionStrategy() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel

    val strategies = buildModel.android().defaultConfig().missingDimensionStrategies()
    assertSize(2, strategies)
    verifyListProperty(strategies[0], listOf("minApi", "minApi18"))
    verifyListProperty(strategies[1], listOf("abi", "x86"))

    val freeStrategies = buildModel.android().productFlavors()[0].missingDimensionStrategies()
    assertSize(1, freeStrategies)
    verifyListProperty(freeStrategies[0], listOf("minApi", "minApi23"))
  }

  @Test
  fun testAddMissingDimensionStrategy() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_ADD_MISSING_DIMENSION_STRATEGY)

    val buildModel = gradleBuildModel

    run {
      val newDim = buildModel.android().defaultConfig().addMissingDimensionStrategy("dim", "val1", "val2")
      verifyListProperty(newDim, listOf("dim", "val1", "val2"))
    }

    run {
      val newDim = buildModel.android().defaultConfig().missingDimensionStrategies()[0]
      verifyListProperty(newDim, listOf("dim", "val1", "val2"))

      val otherDim = buildModel.android().defaultConfig().addMissingDimensionStrategy("otherDim", ReferenceTo("refToVal"))
      verifyListProperty(otherDim, listOf("otherDim", "boo"))
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_ADD_MISSING_DIMENSION_STRATEGY_EXPECTED)

    val strategies = buildModel.android().defaultConfig().missingDimensionStrategies()
    assertSize(2, strategies)
    verifyListProperty(strategies[0], listOf("dim", "val1", "val2"))
    verifyListProperty(strategies[1], listOf("otherDim", "boo"))
  }

  @Test
  fun testRemoveMissingDimensionStrategy() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel

    run {
      val strategies = buildModel.android().defaultConfig().missingDimensionStrategies()
      assertSize(2, strategies)
      verifyListProperty(strategies[0], listOf("minApi", "minApi18"))
      verifyListProperty(strategies[1], listOf("abi", "x86"))

      val freeStrategies = buildModel.android().productFlavors()[0].missingDimensionStrategies()
      assertSize(1, freeStrategies)
      verifyListProperty(freeStrategies[0], listOf("minApi", "minApi23"))

      strategies[1].delete()
      freeStrategies[0].delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_MISSING_DIMENSION_STRATEGY_EXPECTED)

    run {
      val strategies = buildModel.android().defaultConfig().missingDimensionStrategies()
      assertSize(1, strategies)
      verifyListProperty(strategies[0], listOf("minApi", "minApi18"))

      val freeStrategies = buildModel.android().productFlavors()[0].missingDimensionStrategies()
      assertEmpty(freeStrategies)
    }
  }

  @Test
  fun testMissingDimensionStrategiesAreModifiedWithChange() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[0].addListValue().setValue("minApi17")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[0].toList()!![2].delete()
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[0].toList()!![1].setValue("maxApi17")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())

    applyChangesAndReparse(buildModel)
    // TODO(b/142114586)
    //verifyFileContents(myBuildFile, "")

    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[0].toList()!![1].setValue("minApi17")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
  }

  @Test
  fun testMissingDimensionStrategiesAreModifiedWithAddition() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().addMissingDimensionStrategy("dim", "val1")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[2].delete()
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
  }

  @Test
  fun testMissingDimensionStrategiesAreUnmodifiedWithAdditionAfterApply() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().addMissingDimensionStrategy("dim", "val1")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())

    applyChangesAndReparse(buildModel)
    // TODO(b/142114586)
    //verifyFileContents(myBuildFile, "")

    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[2].delete()
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
  }

  @Test
  fun testMissingDimensionStrategiesAreModifiedWithDeletion() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[1].delete()
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().addMissingDimensionStrategy("abi", "x86")
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
  }


  @Test
  fun testMissingDimensionStrategiesAreUnmodifiedWithDeletionAfterApply() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_MISSING_DIMENSION_TEXT)

    val buildModel = gradleBuildModel
    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().missingDimensionStrategies()[1].delete()
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())

    applyChangesAndReparse(buildModel)
    // TODO(b/142114586)
    //verifyFileContents(myBuildFile, "")

    assertFalse(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
    buildModel.android().defaultConfig().addMissingDimensionStrategy("abi", "x86")
    assertTrue(buildModel.android().defaultConfig().areMissingDimensionStrategiesModified())
  }

  @Test
  fun testResConfigsInListMethodCall() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_RES_CONFIGS_IN_LIST_METHOD_CALL)

    val buildModel = gradleBuildModel
    val resConfigs = buildModel.android().defaultConfig().resConfigs()
    verifyListProperty(resConfigs, listOf("en", "fr"))

    resConfigs.addListValue().setValue("it")
    verifyListProperty(resConfigs, listOf("en", "fr", "it"))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_RES_CONFIGS_IN_LIST_METHOD_CALL_EXPECTED)

    verifyListProperty(resConfigs, listOf("en", "fr", "it"))
  }

  @Test
  fun testRemoveResConfigInListMethodCall() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_REMOVE_RES_CONFIG_IN_LIST_METHOD_CALL)

    val buildModel = gradleBuildModel
    val resConfigs = buildModel.android().defaultConfig().resConfigs()
    verifyListProperty(resConfigs, listOf("en", "fr"))

    resConfigs.toList()!![0].delete()
    verifyListProperty(resConfigs, listOf("fr"))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_REMOVE_RES_CONFIG_IN_LIST_METHOD_CALL_EXPECTED)

    verifyListProperty(resConfigs, listOf("fr"))
  }

  /**
   * This test ensures that we parse the arguments of setProguardFiles correctly, that they are surfaced by the model
   * and can be edited correctly.
   */
  @Test
  fun testSetProguardFiles() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES)

    val quote = if(isGroovy) "'" else "\""

    val buildModel = gradleBuildModel
    val defaultConfig = buildModel.android().defaultConfig()
    val proguardFiles = defaultConfig.proguardFiles()
    verifyListProperty(proguardFiles, listOf("getDefaultProguardFile(${quote}proguard-android.txt${quote})", "proguard-rules.pro"))
    proguardFiles.addListValue().setValue("value")
    verifyListProperty(proguardFiles, listOf("getDefaultProguardFile(${quote}proguard-android.txt${quote})", "proguard-rules.pro", "value"))
    proguardFiles.toList()!![0].delete()
    verifyListProperty(proguardFiles, listOf("proguard-rules.pro", "value"))

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_EXPECTED)
  }

  /**
   * This test ensures that we parse reference arguments of setProguardFiles correctly, that they are surfaced by the model
   * and can be edited correctly.
   */
  @Test
  fun testSetProguardFilesWithReference() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_WITH_REFERENCE)

    val quote = if(isGroovy) "'" else "\""

    val buildModel = gradleBuildModel
    val defaultConfig = buildModel.android().defaultConfig()
    val proguardFiles = defaultConfig.proguardFiles()
    verifyListProperty(proguardFiles, listOf("getDefaultProguardFile(${quote}proguard-android.txt${quote})", "proguard-rules.pro"))
    val varModel = buildModel.ext().findProperty("list")
    varModel.addListValue().setValue("value")
    verifyListProperty(proguardFiles, listOf("getDefaultProguardFile(${quote}proguard-android.txt${quote})", "proguard-rules.pro", "value"))
    varModel.toList()!![0].delete()
    verifyListProperty(proguardFiles, listOf("proguard-rules.pro", "value"))

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_WITH_REFERENCE_EXPECTED)
  }

  /**
   * This test ensures that setProguardFiles clears all current proguard files.
   */
  @Test
  fun testSetProguardFilesClearsProguardFiles() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_SET_PROGUARD_FILES_CLEARS_PROGUARD_FILES)

    val buildModel = gradleBuildModel
    val proguardFiles = buildModel.android().defaultConfig().proguardFiles()
    val consumerProguardFiles = buildModel.android().defaultConfig().consumerProguardFiles()
    verifyListProperty(proguardFiles, listOf("val1", "val2"))
    verifyListProperty(consumerProguardFiles, listOf("val3", "val4"))
  }

  @Test
  fun testTestInstrumentationRunnerArgumentSingularThenPlural() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_TEST_INSTRUMENTATION_RUNNER_ARGUMENT_SINGULAR_THEN_PLURAL)

    val buildModel = gradleBuildModel
    val testInstrumentationRunnerArguments = buildModel.android().defaultConfig().testInstrumentationRunnerArguments()
    verifyMapProperty(testInstrumentationRunnerArguments, mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"))
  }

  @Test
  fun testTestInstrumentationRunnerArgumentPluralThenSingular() {
    writeToBuildFile(PRODUCT_FLAVOR_MODEL_TEST_INSTRUMENTATION_RUNNER_ARGUMENT_PLURAL_THEN_SINGULAR)

    val buildModel = gradleBuildModel
    val testInstrumentationRunnerArguments = buildModel.android().defaultConfig().testInstrumentationRunnerArguments()
    verifyMapProperty(testInstrumentationRunnerArguments, mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"))
  }
}
