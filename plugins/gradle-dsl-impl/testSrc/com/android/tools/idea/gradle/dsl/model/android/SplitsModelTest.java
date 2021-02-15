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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_ADD_RESET_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_ADD_RESET_STATEMENT_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_BLOCK_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_ONLY_ELEMENTS_IN_THE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_RESET_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_REMOVE_RESET_STATEMENT_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_RESET_AND_INITIALIZE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_RESET_NONE_EXISTING;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_RESET_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_SPLITS_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SPLITS_MODEL_SPLITS_TEXT;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.SplitsModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.AbiModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.DensityModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.LanguageModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link SplitsModel}.
 */
public class SplitsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(SPLITS_MODEL_SPLITS_TEXT);
    verifySplitsValues();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(SPLITS_MODEL_SPLITS_TEXT);
    verifySplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    abi.enable().setValue(false);
    abi.exclude().getListValue("abi-exclude-2").setValue("abi-exclude-3");
    abi.include().getListValue("abi-include-1").setValue("abi-include-3");
    abi.universalApk().setValue(true);

    DensityModel density = splits.density();
    density.auto().setValue(true);
    density.compatibleScreens().getListValue("screen2").setValue("screen3");
    density.enable().setValue(false);
    density.exclude().getListValue("density-exclude-1").setValue("density-exclude-3");
    density.include().getListValue("density-include-2").setValue("density-include-3");

    LanguageModel language = splits.language();
    language.enable().setValue(true);
    language.include().getListValue("language-include-1").setValue("language-include-3");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SPLITS_MODEL_SPLITS_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("enable", Boolean.FALSE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-3"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-3", "abi-include-2"), abi.include());
    assertEquals("universalApk", Boolean.TRUE, abi.universalApk());

    density = splits.density();
    assertEquals("auto", Boolean.TRUE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen3"), density.compatibleScreens());
    assertEquals("enable", Boolean.FALSE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude-3", "density-exclude-2"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-3"), density.include());

    language = splits.language();
    assertEquals("enable", Boolean.TRUE, language.enable());
    assertEquals("include", ImmutableList.of("language-include-3", "language-include-2"), language.include());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(SPLITS_MODEL_ADD_ELEMENTS);
    verifyNullSplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    abi.enable().setValue(true);
    abi.exclude().addListValue().setValue("abi-exclude");
    abi.include().addListValue().setValue("abi-include");
    abi.universalApk().setValue(false);

    DensityModel density = splits.density();
    density.auto().setValue(false);
    density.compatibleScreens().addListValue().setValue("screen");
    density.enable().setValue(true);
    density.exclude().addListValue().setValue("density-exclude");
    density.include().addListValue().setValue("density-include");
    density.include().addListValue().setValue("density-include2");

    LanguageModel language = splits.language();
    language.enable().setValue(false);
    language.include().addListValue().setValue("language-include");
    language.include().addListValue().setValue("language-include2");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SPLITS_MODEL_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("enable", Boolean.TRUE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include"), abi.include());
    assertEquals("universalApk", Boolean.FALSE, abi.universalApk());

    density = splits.density();
    assertEquals("auto", Boolean.FALSE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen"), density.compatibleScreens());
    assertEquals("enable", Boolean.TRUE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include", "density-include2"), density.include());

    language = splits.language();
    assertEquals("enable", Boolean.FALSE, language.enable());
    assertEquals("include", ImmutableList.of("language-include", "language-include2"), language.include());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(SPLITS_MODEL_SPLITS_TEXT);
    verifySplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));

    AbiModel abi = splits.abi();
    assertTrue(hasPsiElement(abi));
    abi.enable().delete();
    abi.exclude().delete();
    abi.include().delete();
    abi.universalApk().delete();

    DensityModel density = splits.density();
    assertTrue(hasPsiElement(density));
    density.auto().delete();
    density.compatibleScreens().delete();
    density.enable().delete();
    density.exclude().delete();
    density.include().delete();

    LanguageModel language = splits.language();
    assertTrue(hasPsiElement(language));
    language.enable().delete();
    language.include().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    verifyNullSplitsValues();
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertFalse(hasPsiElement(splits));
  }

  private void verifySplitsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertEquals("enable", Boolean.TRUE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1", "abi-include-2"), abi.include());
    assertEquals("universalApk", Boolean.FALSE, abi.universalApk());

    DensityModel density = splits.density();
    assertEquals("auto", Boolean.FALSE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen2"), density.compatibleScreens());
    assertEquals("enable", Boolean.TRUE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude-1", "density-exclude-2"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-2"), density.include());

    LanguageModel language = splits.language();
    assertEquals("enable", Boolean.FALSE, language.enable());
    assertEquals("include", ImmutableList.of("language-include-1", "language-include-2"), language.include());
  }

  public void verifyNullSplitsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertMissingProperty("enable", abi.enable());
    assertMissingProperty("exclude", abi.exclude());
    assertMissingProperty("include", abi.include());
    assertMissingProperty("universalApk", abi.universalApk());
    assertFalse(hasPsiElement(abi));

    DensityModel density = splits.density();
    assertMissingProperty("auto", density.auto());
    assertMissingProperty("compatibleScreens", density.compatibleScreens());
    assertMissingProperty("enable", density.enable());
    assertMissingProperty("exclude", density.exclude());
    assertMissingProperty("include", density.include());
    assertFalse(hasPsiElement(density));

    LanguageModel language = splits.language();
    assertMissingProperty("enable", language.enable());
    assertMissingProperty("include", language.include());
    assertFalse(hasPsiElement(language));
  }

  @Test
  public void testRemoveBlockElements() throws Exception {
    writeToBuildFile(SPLITS_MODEL_REMOVE_BLOCK_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));
    assertTrue(hasPsiElement(splits.abi()));
    assertTrue(hasPsiElement(splits.density()));
    assertTrue(hasPsiElement(splits.language()));

    splits.removeAbi();
    splits.removeDensity();
    splits.removeLanguage();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertFalse(hasPsiElement(splits));
    assertFalse(hasPsiElement(splits.abi()));
    assertFalse(hasPsiElement(splits.density()));
    assertFalse(hasPsiElement(splits.language()));
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(SPLITS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1", "abi-include-2"), abi.include());

    DensityModel density = splits.density();
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen2"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude-1", "density-exclude-2"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-2"), density.include());

    LanguageModel language = splits.language();
    assertEquals("include", ImmutableList.of("language-include-1", "language-include-2"), language.include());

    abi.exclude().getListValue("abi-exclude-1").delete();
    abi.include().getListValue("abi-include-2").delete();
    density.compatibleScreens().getListValue("screen1").delete();
    density.exclude().getListValue("density-exclude-2").delete();
    density.include().getListValue("density-include-1").delete();
    language.include().getListValue("language-include-2").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SPLITS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("exclude", ImmutableList.of("abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1"), abi.include());

    density = splits.density();
    assertEquals("compatibleScreens", ImmutableList.of("screen2"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude-1"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-2"), density.include());

    language = splits.language();
    assertEquals("include", ImmutableList.of("language-include-1"), language.include());
  }

  @Test
  public void testRemoveOnlyElementsInTheList() throws Exception {
    writeToBuildFile(SPLITS_MODEL_REMOVE_ONLY_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));

    AbiModel abi = splits.abi();
    assertTrue(hasPsiElement(abi));
    assertEquals("exclude", ImmutableList.of("abi-exclude"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include"), abi.include());

    DensityModel density = splits.density();
    assertTrue(hasPsiElement(density));
    assertEquals("compatibleScreens", ImmutableList.of("screen"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include"), density.include());

    LanguageModel language = splits.language();
    assertTrue(hasPsiElement(language));
    assertEquals("include", ImmutableList.of("language-include"), language.include());

    abi.exclude().getListValue("abi-exclude").delete();
    abi.include().getListValue("abi-include").delete();
    density.compatibleScreens().getListValue("screen").delete();
    density.exclude().getListValue("density-exclude").delete();
    density.include().getListValue("density-include").delete();
    language.include().getListValue("language-include").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertMissingProperty("exclude", abi.exclude());
    assertMissingProperty("include", abi.include());
    assertFalse(hasPsiElement(abi));

    density = splits.density();
    assertMissingProperty("compatibleScreens", density.compatibleScreens());
    assertMissingProperty("exclude", density.exclude());
    assertMissingProperty("include", density.include());

    language = splits.language();
    assertMissingProperty("include", language.include());
    assertFalse(hasPsiElement(language));

    assertFalse(hasPsiElement(splits));
  }

  @Test
  public void testResetStatement() throws Exception {
    writeToBuildFile(SPLITS_MODEL_RESET_STATEMENT);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(splits.abi().reset());
    assertTrue(splits.density().reset());
    assertMissingProperty("abi-include", splits.abi().include());
    assertMissingProperty("density-include", splits.density().include());
  }

  @Test
  public void testResetNoneExisting() throws Exception {
    writeToBuildFile(SPLITS_MODEL_RESET_NONE_EXISTING);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(splits.abi().reset());
    assertTrue(splits.density().reset());
    assertEquals("abi-include", ImmutableList.of("abi-include-2", "abi-include-3"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-3"), splits.density().include());
  }

  @Test
  public void testResetAndInitialize() throws Exception {
    writeToBuildFile(SPLITS_MODEL_RESET_AND_INITIALIZE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(splits.abi().reset());
    assertTrue(splits.density().reset());
    assertEquals("abi-include", ImmutableList.of("abi-include-2", "abi-include-3"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-3"), splits.density().include());
  }

  @Test
  public void testAddResetStatement() throws Exception {
    writeToBuildFile(SPLITS_MODEL_ADD_RESET_STATEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertEquals("abi-include", ImmutableList.of("abi-include-1", "abi-include-2"), splits.abi().include());

    splits.abi().setReset(true);
    splits.density().setReset(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SPLITS_MODEL_ADD_RESET_STATEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertTrue(splits.abi().reset());
    assertTrue(splits.density().reset());
    assertEquals("abi-include", ImmutableList.of("abi-include-1", "abi-include-2"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-1", "density-include-2"), splits.density().include());
  }

  @Test
  public void testRemoveResetStatement() throws Exception {
    writeToBuildFile(SPLITS_MODEL_REMOVE_RESET_STATEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertMissingProperty("abi-include", splits.abi().include());
    assertMissingProperty("density-include", splits.density().include());

    splits.abi().setReset(false);
    splits.density().setReset(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SPLITS_MODEL_REMOVE_RESET_STATEMENT_EXPECTED);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertFalse(splits.abi().reset());
    assertFalse(splits.density().reset());
    assertEquals("abi-include", ImmutableList.of("abi-include-1", "abi-include-2"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-1", "density-include-2"), splits.density().include());
  }
}
