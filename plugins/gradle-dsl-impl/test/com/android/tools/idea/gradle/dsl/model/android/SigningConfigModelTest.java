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

import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADDED_TO_TOP_OF_ANDROID_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADDED_TO_TOP_OF_ANDROID_BLOCK_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_AND_APPLY_SIGNING_CONFIG;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_CONSOLE_READ_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_EDIT_CONSOLE_READ_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_EDIT_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_MULTIPLE_SIGNING_CONFIGS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_PARSE_CONSOLE_READ_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_PARSE_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_RENAME_SIGNING_CONFIG_MODEL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SET_AND_APPLY_SIGNING_CONFIG;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SET_AND_APPLY_SIGNING_CONFIG_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SIGNING_CONFIG_APPLICATION_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SIGNING_CONFIG_ASSIGNMENT_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.SIGNING_CONFIG_MODEL_SIGNING_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.CONSOLE_READ;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.ENVIRONMENT_VARIABLE;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.PLAIN_TEXT;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link SigningConfigModel}.
 */
public class SigningConfigModelTest extends GradleFileModelTestCase {
  @Test
  public void testSigningConfigBlockWithApplicationStatements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testSigningConfigBlockWithAssignmentStatements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SIGNING_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testSigningConfigApplicationStatements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SIGNING_CONFIG_APPLICATION_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testSigningConfigAssignmentStatements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SIGNING_CONFIG_ASSIGNMENT_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testMultipleSigningConfigs() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_MULTIPLE_SIGNING_CONFIGS);
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(2);

    SigningConfigModel signingConfig1 = signingConfigs.get(0);
    assertEquals("signingConfig1", "release", signingConfig1.name());
    assertEquals("signingConfig1", "release.keystore", signingConfig1.storeFile());
    verifyPasswordModel(signingConfig1.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig1", "type1", signingConfig1.storeType());
    assertEquals("signingConfig1", "myReleaseKey", signingConfig1.keyAlias());
    verifyPasswordModel(signingConfig1.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    SigningConfigModel signingConfig2 = signingConfigs.get(1);
    assertEquals("signingConfig1", "debug.keystore", signingConfig2.storeFile());
    verifyPasswordModel(signingConfig2.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig1", "type2", signingConfig2.storeType());
    assertEquals("signingConfig1", "myDebugKey", signingConfig2.keyAlias());
    verifyPasswordModel(signingConfig2.keyPassword(), "debugKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testSetAndApplySigningConfig() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SET_AND_APPLY_SIGNING_CONFIG);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    signingConfig.storeFile().setValue("debug.keystore");
    signingConfig.storePassword().setValue(PLAIN_TEXT, "debug_password");
    signingConfig.storeType().setValue("debug_type");
    signingConfig.keyAlias().setValue("myDebugKey");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "debugKeyPassword");

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_SET_AND_APPLY_SIGNING_CONFIG_EXPECTED);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "debugKeyPassword", PLAIN_TEXT);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "debugKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testRemoveAndApplySigningConfig() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_REMOVE_AND_APPLY_SIGNING_CONFIG);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    List<SigningConfigModel> signingConfigs = android.signingConfigs();

    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    signingConfig.keyAlias().delete();
    signingConfig.keyPassword().delete();
    signingConfig.storeType().delete();
    signingConfig.storeFile().delete();
    signingConfig.storePassword().delete();

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertMissingProperty(signingConfig.storeFile());
    assertMissingProperty(signingConfig.storePassword());
    assertMissingProperty(signingConfig.storeType());
    assertMissingProperty(signingConfig.keyAlias());
    assertMissingProperty(signingConfig.keyPassword());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).isEmpty(); // empty blocks are deleted automatically.
  }

  @Test
  public void testAddAndApplySigningConfig() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_ADD_AND_APPLY_SIGNING_CONFIG);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertMissingProperty(signingConfig.storeFile());
    assertMissingProperty(signingConfig.storePassword());
    assertMissingProperty(signingConfig.keyAlias());
    assertMissingProperty(signingConfig.keyPassword());

    signingConfig.storeFile().setValue("release.keystore");
    signingConfig.storePassword().setValue(PLAIN_TEXT, "password");
    signingConfig.storeType().setValue("type");
    signingConfig.keyAlias().setValue("myReleaseKey");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "releaseKeyPassword");

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_ADD_AND_APPLY_SIGNING_CONFIG_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  @Test
  public void testParseEnvironmentVariablePasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_PARSE_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);
  }

  @Test
  public void testParseConsoleReadPasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_PARSE_CONSOLE_READ_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    // TODO(karimai) : verify behaviour with escape characters.
    verifyPasswordModel(signingConfig.storePassword(), "Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "Key password: ", CONSOLE_READ);
  }

  @Test
  public void testEditEnvironmentVariablePasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);

    signingConfig.storePassword().setValue(ENVIRONMENT_VARIABLE, "KSTOREPWD1");
    signingConfig.keyPassword().setValue(ENVIRONMENT_VARIABLE, "KEYPWD1");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD1", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD1", ENVIRONMENT_VARIABLE);
  }

  @Test
  public void testEditConsoleReadPasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_EDIT_CONSOLE_READ_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "Key password: ", CONSOLE_READ);

    signingConfig.storePassword().setValue(CONSOLE_READ, "Another Keystore Password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, "Another Key Password: ");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_EDIT_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "Another Keystore Password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "Another Key Password: ", CONSOLE_READ);
  }

  @Test
  public void testAddEnvironmentVariablePasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertMissingProperty("signingConfig", signingConfig.storePassword());
    assertMissingProperty("signingConfig", signingConfig.keyPassword());

    signingConfig.storePassword().setValue(ENVIRONMENT_VARIABLE, "KSTOREPWD");
    signingConfig.keyPassword().setValue(ENVIRONMENT_VARIABLE, "KEYPWD");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);
  }

  @Test
  public void testAddConsoleReadPasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_ADD_CONSOLE_READ_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertMissingProperty("signingConfig", signingConfig.storePassword());
    assertMissingProperty("signingConfig", signingConfig.keyPassword());

    signingConfig.storePassword().setValue(CONSOLE_READ, /*"\n*/"Keystore password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, /*"\n*/"Key password: ");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_ADD_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), /*"\n*/"Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), /*"\n*/"Key password: ", CONSOLE_READ);
  }

  @Test
  public void testChangeEnvironmentVariablePasswordToConsoleReadPassword() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);


    signingConfig.storePassword().setValue(CONSOLE_READ, /*"\n*/"Keystore password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, /*"\n*/"Key password: ");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), /*"\n*/"Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), /*"\n*/"Key password: ", CONSOLE_READ);
  }

  @Test
  public void testChangeConsoleReadPasswordElementsToPlainTextPasswordElements() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "Key password: ", CONSOLE_READ);

    signingConfig.storePassword().setValue(PLAIN_TEXT, "store_password");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "key_password");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "store_password", PLAIN_TEXT);
    verifyPasswordModel(signingConfig.keyPassword(), "key_password", PLAIN_TEXT);
  }

  @Test
  public void testRenameSigningConfigModel() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();

    List<SigningConfigModel> signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("release", signingConfigs.get(0).name());
    assertEquals("myReleaseKey", signingConfigs.get(0).keyAlias().toString());

    String expectedName = "newName";
    signingConfigs.get(0).rename(expectedName);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_RENAME_SIGNING_CONFIG_MODEL_EXPECTED);

    androidModel = buildModel.android();
    assertNotNull(androidModel);

    signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("newName", signingConfigs.get(0).name());
    assertEquals("myReleaseKey", signingConfigs.get(0).keyAlias().toString());
  }

  @Test
  public void testSigningConfigAddedToTopOfAndroidBlock() throws Exception {
    writeToBuildFile(SIGNING_CONFIG_MODEL_ADDED_TO_TOP_OF_ANDROID_BLOCK);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();

    SigningConfigModel signingConfig = androidModel.addSigningConfig("release");
    signingConfig.storeFile().setValue(new ReferenceTo("keystorefile"));
    signingConfig.storePassword().setValue("123456");
    signingConfig.keyAlias().setValue("demo");
    signingConfig.keyPassword().setValue("123456");

    BuildTypeModel buildType = androidModel.buildTypes().stream().filter(type -> type.name().equals("release")).findFirst().orElse(null);
    assertNotNull(buildType);

    buildType.signingConfig().setValue(new ReferenceTo(signingConfig));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SIGNING_CONFIG_MODEL_ADDED_TO_TOP_OF_ANDROID_BLOCK_EXPECTED);
  }
}
