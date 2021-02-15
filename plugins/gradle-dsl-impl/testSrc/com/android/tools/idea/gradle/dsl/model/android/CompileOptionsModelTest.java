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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_ADD;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_ADD_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_APPLICATION_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK_USING_ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK_WITH_OVERRIDE_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_LONG_IDENTIFIER;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_LONG_IDENTIFIER_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_REMOVE_APPLICATION_STATEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_REMOVE_APPLICATION_STATEMENT_EXPECTED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;
import org.junit.Test;

public class CompileOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testCompileOptionsBlock() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals("encoding", "UTF8", compileOptions.encoding());
    assertEquals("incremental", Boolean.TRUE, compileOptions.incremental());
  }

  @Test
  public void testCompileOptionsBlockUsingAssignment() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK_USING_ASSIGNMENT);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals("encoding", "UTF8", compileOptions.encoding());
    assertEquals("incremental", Boolean.FALSE, compileOptions.incremental());
  }

  @Test
  public void testCompileOptionsApplicationStatement() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_APPLICATION_STATEMENT);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility().toLanguageLevel());
  }

  // TODO test the case of remove sourceCompatibility with override
  @Test
  public void testCompileOptionsBlockWithOverrideStatement() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_BLOCK_WITH_OVERRIDE_STATEMENT);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testCompileOptionsRemoveApplicationStatement() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_REMOVE_APPLICATION_STATEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    compileOptions.sourceCompatibility().delete();
    compileOptions.targetCompatibility().delete();
    compileOptions.encoding().delete();
    compileOptions.incremental().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_REMOVE_APPLICATION_STATEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    checkForInValidPsiElement(compileOptions, CompileOptionsModelImpl.class);
    assertMissingProperty(compileOptions.sourceCompatibility());
    assertMissingProperty(compileOptions.targetCompatibility());
    assertMissingProperty(compileOptions.encoding());
    assertMissingProperty(compileOptions.incremental());
  }

  @Test
  public void testCompileOptionsModify() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.BIG_DECIMAL, compileOptions.sourceCompatibility().getValueType());
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.STRING, compileOptions.targetCompatibility().getValueType());
    assertEquals("encoding", "UTF8", compileOptions.encoding());
    assertEquals("incremental", Boolean.FALSE, compileOptions.incremental());

    compileOptions.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8);
    compileOptions.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_9);
    compileOptions.encoding().setValue("ISO-2022-JP");
    compileOptions.incremental().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_8, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.BIG_DECIMAL, compileOptions.sourceCompatibility().getValueType());
    assertEquals(LanguageLevel.JDK_1_9, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.STRING, compileOptions.targetCompatibility().getValueType());
    assertEquals("encoding", "ISO-2022-JP", compileOptions.encoding());
    assertEquals("incremental", Boolean.TRUE, compileOptions.incremental());
  }

  @Test
  public void testCompileOptionsModify_longIdentier() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_LONG_IDENTIFIER);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.CUSTOM, compileOptions.sourceCompatibility().getValueType());
    assertNull(compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.STRING, compileOptions.targetCompatibility().getValueType());

    compileOptions.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8);
    compileOptions.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_9);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_MODIFY_LONG_IDENTIFIER_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_8, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.CUSTOM, compileOptions.sourceCompatibility().getValueType());
    assertEquals(LanguageLevel.JDK_1_9, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals(GradlePropertyModel.ValueType.STRING, compileOptions.targetCompatibility().getValueType());
  }

  @Test
  public void testCompileOptionsAdd() throws Exception {
    writeToBuildFile(COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_ADD);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertMissingProperty(compileOptions.sourceCompatibility());
    assertMissingProperty(compileOptions.targetCompatibility());
    assertMissingProperty(compileOptions.encoding());
    assertMissingProperty(compileOptions.incremental());

    compileOptions.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_6);
    compileOptions.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7);
    compileOptions.encoding().setValue("UTF8");
    compileOptions.incremental().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, COMPILE_OPTIONS_MODEL_COMPILE_OPTIONS_ADD_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.targetCompatibility().toLanguageLevel());
    assertEquals("encoding", "UTF8", compileOptions.encoding());
    assertEquals("incremental", Boolean.TRUE, compileOptions.incremental());
  }
}