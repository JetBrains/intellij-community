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
package com.android.tools.idea.gradle.dsl.model.android;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_ADD_EMPTY_PRODUCT_FLAVOR;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_ADD_EMPTY_PRODUCT_FLAVOR_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_ADD_PRODUCT_FLAVOR;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_ADD_PRODUCT_FLAVOR_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_NOT_REMOVED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_NOT_REMOVED_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_APPEND_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_APPLICATION_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_ASSIGNMENT_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_OVERRIDE_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PRODUCT_FLAVORS_ELEMENT_RENAME_PRODUCT_FLAVOR_EXPECTED;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link ProductFlavorsDslElement}.
 *
 * <p>Both {@code android.defaultConfig {}} and {@code android.productFlavors.xyz {}} uses the same structure with same attributes.
 * In this test, we only test the general structure of {@code android.productFlavors {}}. The product flavor structure defined by
 * {@link ProductFlavorModelImpl} is tested in great deal to cover all combinations in {@link ProductFlavorModelTest} using the
 * {@code android.defaultConfig {}} block.
 */
public class ProductFlavorsElementTest extends GradleFileModelTestCase {
  @Test
  public void testProductFlavorsWithApplicationStatements() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_APPLICATION_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);

    ProductFlavorModel flavor1 = productFlavors.get(0);
    assertEquals("applicationId", "com.example.myFlavor1", flavor1.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), flavor1.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                 flavor1.testInstrumentationRunnerArguments());
    ProductFlavorModel flavor2 = productFlavors.get(1);
    assertEquals("applicationId", "com.example.myFlavor2", flavor2.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), flavor2.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4"),
                 flavor2.testInstrumentationRunnerArguments());
  }

  @Test
  public void testProductFlavorsWithAssignmentStatements() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_ASSIGNMENT_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertSize(2, productFlavors);

    ProductFlavorModel flavor1 = productFlavors.get(0);
    assertEquals("applicationId", "com.example.myFlavor1", flavor1.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), flavor1.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                 flavor1.testInstrumentationRunnerArguments());

    ProductFlavorModel flavor2 = productFlavors.get(1);
    assertEquals("applicationId", "com.example.myFlavor2", flavor2.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), flavor2.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4"),
                 flavor2.testInstrumentationRunnerArguments());
  }

  @Test
  public void testProductFlavorsWithOverrideStatements() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_OVERRIDE_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);

    ProductFlavorModel flavor1 = productFlavors.get(0);
    assertEquals("applicationId", "com.example.myFlavor1-1", flavor1.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-3.txt", "proguard-rules-3.txt"), flavor1.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key5", "value5", "key6", "value6"),
                 flavor1.testInstrumentationRunnerArguments());

    ProductFlavorModel flavor2 = productFlavors.get(1);
    assertEquals("applicationId", "com.example.myFlavor2-1", flavor2.applicationId());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-4.txt", "proguard-rules-4.txt"), flavor2.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key7", "value7", "key8", "value8"),
                 flavor2.testInstrumentationRunnerArguments());
  }

  @Test
  public void testProductFlavorsWithAppendStatements() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_APPEND_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(2);

    ProductFlavorModel flavor1 = productFlavors.get(0);
    assertEquals("proguardFiles",
                 ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt", "proguard-android-3.txt", "proguard-rules-3.txt"),
                 flavor1.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2", "key5", "value5"),
                 flavor1.testInstrumentationRunnerArguments());

    ProductFlavorModel flavor2 = productFlavors.get(1);
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt", "proguard-android-4.txt"),
                 flavor2.proguardFiles());
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4", "key6", "value6"),
                 flavor2.testInstrumentationRunnerArguments());
  }

  @Test
  public void testAddEmptyProductFlavor() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_ADD_EMPTY_PRODUCT_FLAVOR);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    android.addProductFlavor("flavorA");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, PRODUCT_FLAVORS_ELEMENT_ADD_EMPTY_PRODUCT_FLAVOR_EXPECTED);
    android = buildModel.android();

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);

    ProductFlavorModel productFlavor = productFlavors.get(0);
    assertEquals("name", "flavorA", productFlavor.name());
    assertMissingProperty("applicationId", productFlavor.applicationId());
    assertMissingProperty("consumerProguardFiles", productFlavor.consumerProguardFiles());
    assertMissingProperty("dimension", productFlavor.dimension());
    verifyEmptyMapProperty("manifestPlaceholders", productFlavor.manifestPlaceholders());
    assertMissingProperty("maxSdkVersion", productFlavor.maxSdkVersion());
    assertMissingProperty("minSdkVersion", productFlavor.minSdkVersion());
    assertMissingProperty("multiDexEnabled", productFlavor.multiDexEnabled());
    assertMissingProperty("proguardFiles", productFlavor.proguardFiles());
    assertMissingProperty("resConfigs", productFlavor.resConfigs());
    assertEmpty("resValues", productFlavor.resValues());
    assertMissingProperty("targetSdkVersion", productFlavor.targetSdkVersion());
    assertMissingProperty("testApplicationId", productFlavor.testApplicationId());
    assertMissingProperty("testFunctionalTest", productFlavor.testFunctionalTest());
    assertMissingProperty("testHandleProfiling", productFlavor.testHandleProfiling());
    assertMissingProperty("testInstrumentationRunner", productFlavor.testInstrumentationRunner());
    verifyEmptyMapProperty("testInstrumentationRunnerArguments", productFlavor.testInstrumentationRunnerArguments());
    assertMissingProperty("useJack", productFlavor.useJack());
    assertMissingProperty("versionCode", productFlavor.versionCode());
    assertMissingProperty("versionName", productFlavor.versionName());
  }

  @Test
  public void testAddProductFlavor() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_ADD_PRODUCT_FLAVOR);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    android.addProductFlavor("flavorA");
    android.productFlavors().get(0).applicationId().setValue("appid");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, PRODUCT_FLAVORS_ELEMENT_ADD_PRODUCT_FLAVOR_EXPECTED);

    android = buildModel.android();

    List<ProductFlavorModel> productFlavors = android.productFlavors();
    assertThat(productFlavors).hasSize(1);

    ProductFlavorModel productFlavor = productFlavors.get(0);
    assertEquals("name", "flavorA", productFlavor.name());
    assertEquals("applicationId", "appid", productFlavor.applicationId());
  }

  @Test
  public void testProductFlavorsNotRemoved() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_NOT_REMOVED);

    GradleBuildModel buildModel = getGradleBuildModel();
    ProductFlavorModel xModel = buildModel.android().productFlavors().get(0);
    xModel.externalNativeBuild().removeCMake();

    checkForValidPsiElement(xModel, ProductFlavorModelImpl.class);

    applyChangesAndReparse(buildModel);

    checkForValidPsiElement(buildModel.android().productFlavors().get(0), ProductFlavorModelImpl.class);
    verifyFileContents(myBuildFile, PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_NOT_REMOVED_EXPECTED);
  }

  @Test
  public void testRenameProductFlavor() throws Exception {
    writeToBuildFile(PRODUCT_FLAVORS_ELEMENT_PRODUCT_FLAVORS_WITH_APPLICATION_STATEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ProductFlavorModel> productFlavors = buildModel.android().productFlavors();
    assertThat(productFlavors).hasSize(2);

    ProductFlavorModel flavor1 = productFlavors.get(0);
    flavor1.rename("newAndImproved");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, PRODUCT_FLAVORS_ELEMENT_RENAME_PRODUCT_FLAVOR_EXPECTED);

    assertEquals("newAndImproved", buildModel.android().productFlavors().get(0).name());
    assertEquals("com.example.myFlavor1", buildModel.android().productFlavors().get(0).applicationId().toString());
  }
}
