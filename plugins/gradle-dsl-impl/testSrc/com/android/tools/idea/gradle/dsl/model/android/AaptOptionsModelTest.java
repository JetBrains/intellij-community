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
package com.android.tools.idea.gradle.dsl.model.android;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_EDIT_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_EDIT_IGNORE_ASSET_PATTERN;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_EDIT_IGNORE_ASSET_PATTERN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_PARSE_ELEMENTS_ONE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_PARSE_ELEMENTS_TWO;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_REMOVE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_REMOVE_LAST_ELEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_REMOVE_ONE_ELEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.AAPT_OPTIONS_REMOVE_ONE_ELEMENT_EXPECTED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link AaptOptionsModel}.
 */
public class AaptOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsOne() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_PARSE_ELEMENTS_ONE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  @Test
  public void testParseElementsTwo() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_PARSE_ELEMENTS_TWO);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_EDIT_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());

    aaptOptions.additionalParameters().getListValue("efgh").setValue("xyz");
    aaptOptions.cruncherEnabled().setValue(true);
    aaptOptions.cruncherProcesses().setValue(3);
    aaptOptions.failOnMissingConfigEntry().setValue(false);
    aaptOptions.ignoreAssets().setValue("mnop");
    aaptOptions.noCompress().getListValue("b").setValue("c");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, AAPT_OPTIONS_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "xyz"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(3), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "mnop", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "c"), aaptOptions.noCompress());
  }

  @Test
  public void testEditIgnoreAssetPattern() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_EDIT_IGNORE_ASSET_PATTERN);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("ignoreAssets", "ijkl", aaptOptions.ignoreAssets());

    aaptOptions.ignoreAssets().setValue("mnop");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, AAPT_OPTIONS_EDIT_IGNORE_ASSET_PATTERN_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("ignoreAssets", "mnop", aaptOptions.ignoreAssets());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertMissingProperty("additionalParameters", aaptOptions.additionalParameters());
    assertMissingProperty("cruncherEnabled", aaptOptions.cruncherEnabled());
    assertMissingProperty("cruncherProcesses", aaptOptions.cruncherProcesses());
    assertMissingProperty("failOnMissingConfigEntry", aaptOptions.failOnMissingConfigEntry());
    assertMissingProperty("ignoreAssets", aaptOptions.ignoreAssets());
    assertMissingProperty("noCompress", aaptOptions.noCompress());

    aaptOptions.additionalParameters().addListValue().setValue("abcd");
    aaptOptions.cruncherEnabled().setValue(true);
    aaptOptions.cruncherProcesses().setValue(1);
    aaptOptions.failOnMissingConfigEntry().setValue(false);
    aaptOptions.ignoreAssets().setValue("efgh");
    aaptOptions.noCompress().addListValue().setValue("a");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, AAPT_OPTIONS_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    checkForValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());

    aaptOptions.additionalParameters().delete();
    aaptOptions.cruncherEnabled().delete();
    aaptOptions.cruncherProcesses().delete();
    aaptOptions.failOnMissingConfigEntry().delete();
    aaptOptions.ignoreAssets().delete();
    aaptOptions.noCompress().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    checkForInValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", aaptOptions.additionalParameters());
    assertMissingProperty("cruncherEnabled", aaptOptions.cruncherEnabled());
    assertMissingProperty("cruncherProcesses", aaptOptions.cruncherProcesses());
    assertMissingProperty("failOnMissingConfigEntry", aaptOptions.failOnMissingConfigEntry());
    assertMissingProperty("ignoreAssets", aaptOptions.ignoreAssets());
    assertMissingProperty("noCompress", aaptOptions.noCompress());
  }

  @Test
  public void testRemoveOneElementsInList() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_REMOVE_ONE_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());

    aaptOptions.additionalParameters().getListValue("abcd").delete();
    aaptOptions.noCompress().getListValue("b").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, AAPT_OPTIONS_REMOVE_ONE_ELEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  @Test
  public void testRemoveLastElementInList() throws Exception {
    writeToBuildFile(AAPT_OPTIONS_REMOVE_LAST_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    checkForValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());

    aaptOptions.additionalParameters().getListValue("abcd").delete();
    aaptOptions.noCompress().getListValue("a").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    checkForInValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", aaptOptions.additionalParameters());
    assertMissingProperty("noCompress", aaptOptions.noCompress());
  }
}
