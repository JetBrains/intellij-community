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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.CONSOLE_READ;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.ENVIRONMENT_VARIABLE;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.PLAIN_TEXT;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link SigningConfigModel}.
 */
public class SigningConfigModelTest extends GradleFileModelTestCase {
  @Test
  public void testSigningConfigBlockWithApplicationStatements() throws Exception {
    writeToBuildFile(TestFile.SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS);
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
    writeToBuildFile(TestFile.SIGNING_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS);
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
    writeToBuildFile(TestFile.SIGNING_CONFIG_APPLICATION_STATEMENTS);
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
    writeToBuildFile(TestFile.SIGNING_CONFIG_ASSIGNMENT_STATEMENTS);
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
    writeToBuildFile(TestFile.MULTIPLE_SIGNING_CONFIGS);
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
    writeToBuildFile(TestFile.SET_AND_APPLY_SIGNING_CONFIG);
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
    verifyFileContents(myBuildFile, TestFile.SET_AND_APPLY_SIGNING_CONFIG_EXPECTED);
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
  public void testRemoveStoreFileAndApplySigningConfig() throws Exception {
    writeToBuildFile(TestFile.REMOVE_STORE_FILE_AND_APPLY_SIGNING_CONFIG);
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

    signingConfig.storeFile().delete();

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_STORE_FILE_AND_APPLY_SIGNING_CONFIG_EXPECTED);
  }

  @Test
  public void testRemoveAndApplySigningConfig() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_SIGNING_CONFIG);
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
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_SIGNING_CONFIG_EXPECTED);

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
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertMissingProperty(signingConfig.storeFile());
    assertMissingProperty(signingConfig.storePassword());
    assertMissingProperty(signingConfig.storeType());
    assertMissingProperty(signingConfig.keyAlias());
    assertMissingProperty(signingConfig.keyPassword());
  }

  @Test
  public void testAddAndApplySigningConfig() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_SIGNING_CONFIG);
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
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_SIGNING_CONFIG_EXPECTED);

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
    writeToBuildFile(TestFile.PARSE_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
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
    writeToBuildFile(TestFile.PARSE_CONSOLE_READ_PASSWORD_ELEMENTS);
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
    writeToBuildFile(TestFile.EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
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
    verifyFileContents(myBuildFile, TestFile.EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED);

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
    writeToBuildFile(TestFile.EDIT_CONSOLE_READ_PASSWORD_ELEMENTS);
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
    verifyFileContents(myBuildFile, TestFile.EDIT_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED);

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
    writeToBuildFile(TestFile.ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS);
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
    verifyFileContents(myBuildFile, TestFile.ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED);

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
    writeToBuildFile(TestFile.ADD_CONSOLE_READ_PASSWORD_ELEMENTS);
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
    verifyFileContents(myBuildFile, TestFile.ADD_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED);

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
    writeToBuildFile(TestFile.CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD);
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
    verifyFileContents(myBuildFile, TestFile.CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD_EXPECTED);

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
    writeToBuildFile(TestFile.CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS);
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
    verifyFileContents(myBuildFile, TestFile.CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS_EXPECTED);

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
    writeToBuildFile(TestFile.SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();

    List<SigningConfigModel> signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("release", signingConfigs.get(0).name());
    assertEquals("myReleaseKey", signingConfigs.get(0).keyAlias().toString());

    String expectedName = "newName";
    signingConfigs.get(0).rename(expectedName);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_EXPECTED);

    androidModel = buildModel.android();
    assertNotNull(androidModel);

    signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    assertEquals("newName", signingConfigs.get(0).name());
    assertEquals("myReleaseKey", signingConfigs.get(0).keyAlias().toString());
  }

  @Test
  public void testRenameWithReferences() throws Exception {
    writeToBuildFile(TestFile.RENAME_WITH_REFERENCES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();

    List<SigningConfigModel> signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel release = signingConfigs.get(0);
    assertEquals("release", release.name());
    assertEquals("release.keystore", release.storeFile().toString());
    assertEquals("storePassword", release.storePassword().toString());
    assertEquals("PKCS12", release.storeType().toString());
    assertEquals("myReleaseKey", release.keyAlias().toString());
    assertEquals("keyPassword", release.keyPassword().toString());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals(release.name(), defaultConfig.signingConfig().toSigningConfig().name());

    // TODO(xof): I'm really not convinced that this is what the property *should* look like.  The SingleArgumentMethodTransform is
    //  presumably not firing on the value of the reference.  Also, writing out the renamed reference is currently broken
    // verifyPropertyModel(defaultConfig.multiDexKeepFile(), STRING_TYPE, "file(\"release.keystore\")", UNKNOWN, REGULAR, 1);

    //  ... these are OK though
    verifyPropertyModel(defaultConfig.applicationIdSuffix(), STRING_TYPE, "storePassword", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testInstrumentationRunner(), STRING_TYPE, "PKCS12", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testApplicationId(), STRING_TYPE, "myReleaseKey", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.versionName(), STRING_TYPE, "keyPassword", STRING, REGULAR, 1);

    release.rename("newRelease", true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_WITH_REFERENCES_EXPECTED);

    androidModel = buildModel.android();
    signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel newRelease = signingConfigs.get(0);
    assertEquals("newRelease", newRelease.name());
    assertEquals("release.keystore", newRelease.storeFile().toString());
    assertEquals("storePassword", newRelease.storePassword().toString());
    assertEquals("PKCS12", newRelease.storeType().toString());
    assertEquals("myReleaseKey", newRelease.keyAlias().toString());
    assertEquals("keyPassword", newRelease.keyPassword().toString());

    defaultConfig = androidModel.defaultConfig();
    assertEquals(newRelease.name(), defaultConfig.signingConfig().toSigningConfig().name());

    // TODO(xof): see above
    // verifyPropertyModel(defaultConfig.multiDexKeepFile(), STRING_TYPE, "file(\"release.keystore\")", UNKNOWN, REGULAR, 1);
    verifyPropertyModel(defaultConfig.applicationIdSuffix(), STRING_TYPE, "storePassword", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testInstrumentationRunner(), STRING_TYPE, "PKCS12", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testApplicationId(), STRING_TYPE, "myReleaseKey", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.versionName(), STRING_TYPE, "keyPassword", STRING, REGULAR, 1);
  }

  @Test
  public void testRenameTrickyWithReferences() throws Exception {
    writeToBuildFile(TestFile.RENAME_TRICKY_WITH_REFERENCES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();

    List<SigningConfigModel> signingConfigs = androidModel.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel release = signingConfigs.get(0);
    assertEquals("my release.", release.name());
    assertEquals("release.keystore", release.storeFile().toString());
    assertEquals("storePassword", release.storePassword().toString());
    assertEquals("PKCS12", release.storeType().toString());
    assertEquals("myReleaseKey", release.keyAlias().toString());
    assertEquals("keyPassword", release.keyPassword().toString());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals(release.name(), defaultConfig.signingConfig().toSigningConfig().name());

    // TODO(xof): see above
    // verifyPropertyModel(defaultConfig.multiDexKeepFile(), STRING_TYPE, "file(\"release.keystore\")", UNKNOWN, REGULAR, 1);
    verifyPropertyModel(defaultConfig.applicationIdSuffix(), STRING_TYPE, "storePassword", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testInstrumentationRunner(), STRING_TYPE, "PKCS12", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.testApplicationId(), STRING_TYPE, "myReleaseKey", STRING, REGULAR, 1);
    verifyPropertyModel(defaultConfig.versionName(), STRING_TYPE, "keyPassword", STRING, REGULAR, 1);

    release.rename("my new release.", true);
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_TRICKY_WITH_REFERENCES_EXPECTED);
  }

  @Test
  public void testSigningConfigAddedToTopOfAndroidBlock() throws Exception {
    writeToBuildFile(TestFile.ADDED_TO_TOP_OF_ANDROID_BLOCK);

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
    verifyFileContents(myBuildFile, TestFile.ADDED_TO_TOP_OF_ANDROID_BLOCK_EXPECTED);
  }

  enum TestFile implements TestFileName {
    SIGNING_CONFIG_BLOCK_WITH_APPLICATION_STATEMENTS("signingConfigBlockWithApplicationStatements"),
    RENAME_EXPECTED("renameSigningConfigModelExpected"),
    RENAME_WITH_REFERENCES("renameWithReferences"),
    RENAME_WITH_REFERENCES_EXPECTED("renameWithReferencesExpected"),
    RENAME_TRICKY_WITH_REFERENCES("renameTrickyWithReferences"),
    RENAME_TRICKY_WITH_REFERENCES_EXPECTED("renameTrickyWithReferencesExpected"),
    SIGNING_CONFIG_BLOCK_WITH_ASSIGNMENT_STATEMENTS("signingConfigBlockWithAssignmentStatements"),
    SIGNING_CONFIG_APPLICATION_STATEMENTS("signingConfigApplicationStatements"),
    SIGNING_CONFIG_ASSIGNMENT_STATEMENTS("signingConfigAssignmentStatements"),
    MULTIPLE_SIGNING_CONFIGS("multipleSigningConfigs"),
    SET_AND_APPLY_SIGNING_CONFIG("setAndApplySigningConfig"),
    SET_AND_APPLY_SIGNING_CONFIG_EXPECTED("setAndApplySigningConfigExpected"),
    REMOVE_AND_APPLY_SIGNING_CONFIG("removeAndApplySigningConfig"),
    REMOVE_AND_APPLY_SIGNING_CONFIG_EXPECTED("removeAndApplySigningConfigExpected"),
    REMOVE_STORE_FILE_AND_APPLY_SIGNING_CONFIG("removeStoreFileAndApplySigningConfig"),
    REMOVE_STORE_FILE_AND_APPLY_SIGNING_CONFIG_EXPECTED("removeStoreFileAndApplySigningConfigExpected"),
    ADD_AND_APPLY_SIGNING_CONFIG("addAndApplySigningConfig"),
    ADD_AND_APPLY_SIGNING_CONFIG_EXPECTED("addAndApplySigningConfigExpected"),
    PARSE_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS("parseEnvironmentVariablePasswordElements"),
    PARSE_CONSOLE_READ_PASSWORD_ELEMENTS("parseConsoleReadPasswordElements"),
    EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS("editEnvironmentVariablePasswordElements"),
    EDIT_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED("editEnvironmentVariablePasswordElementsExpected"),
    EDIT_CONSOLE_READ_PASSWORD_ELEMENTS("editConsoleReadPasswordElements"),
    EDIT_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED("editConsoleReadPasswordElementsExpected"),
    ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS("addEnvironmentVariablePasswordElements"),
    ADD_ENVIRONMENT_VARIABLE_PASSWORD_ELEMENTS_EXPECTED("addEnvironmentVariablePasswordElementsExpected"),
    ADD_CONSOLE_READ_PASSWORD_ELEMENTS("addConsoleReadPasswordElements"),
    ADD_CONSOLE_READ_PASSWORD_ELEMENTS_EXPECTED("addConsoleReadPasswordElementsExpected"),
    CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD("changeEnvironmentVariablePasswordToConsoleReadPassword"),
    CHANGE_ENVIRONMENT_VARIABLE_PASSWORD_TO_CONSOLE_READ_PASSWORD_EXPECTED("changeEnvironmentVariablePasswordToConsoleReadPasswordExpected"),
    CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS("changeConsoleReadPasswordElementsToPlainTextPasswordElements"),
    CHANGE_CONSOLE_READ_PASSWORD_ELEMENTS_TO_PLAIN_TEXT_PASSWORD_ELEMENTS_EXPECTED("changeConsoleReadPasswordElementsToPlainTextPasswordElementsExpected"),
    ADDED_TO_TOP_OF_ANDROID_BLOCK("addedToTopOfAndroidBlock"),
    ADDED_TO_TOP_OF_ANDROID_BLOCK_EXPECTED("addedToTopOfAndroidBlockExpected"),
    ;

    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/signingConfigModel/" + path, extension);
    }
  }
}
