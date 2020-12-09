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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_APPLY_CHANGES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_APPLY_CHANGES_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_APPLY_CHANGES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_APPLY_CHANGES_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_C_MAKE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_C_MAKE_WITH_NEW_FILE_PATH;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_NDK_BUILD;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_NDK_BUILD_WITH_NEW_FILE_PATH;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_C_MAKE_AND_APPLY_CHANGES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_C_MAKE_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_NDK_BUILD_AND_APPLY_CHANGES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_NDK_BUILD_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_SET_CONSTRUCTOR_TO_FUNCTION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXTERNAL_NATIVE_BUILD_MODEL_SET_CONSTRUCTOR_TO_FUNCTION_EXPECTED;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.CMakeModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.NdkBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.NdkBuildModelImpl;
import org.junit.Test;

/**
 * Tests for {@link ExternalNativeBuildModelImpl}.
 */
public class ExternalNativeBuildModelTest extends GradleFileModelTestCase {
  @Test
  public void testCMake() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_C_MAKE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testCMakeWithNewFilePath() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_C_MAKE_WITH_NEW_FILE_PATH);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testRemoveCMakeAndReset() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_C_MAKE_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_C_MAKE_AND_RESET);
  }

  @Test
  public void testRemoveCMakeAndApplyChanges() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_C_MAKE_AND_APPLY_CHANGES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");

    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class); // empty blocks removed
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);
  }

  @Test
  public void testAddCMakePathAndReset() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    CMakeModel cmake = externalNativeBuild.cmake();
    assertMissingProperty(cmake.path());

    cmake.path().setValue("foo/bar");
    assertEquals("path", "foo/bar", cmake.path());

    buildModel.resetState();
    assertMissingProperty(cmake.path());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_RESET);
  }

  @Test
  public void testAddCMakePathAndApplyChanges() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_APPLY_CHANGES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CMakeModel cmake = android.externalNativeBuild().cmake();
    assertMissingProperty(cmake.path());

    cmake.path().setValue("foo/bar");
    assertEquals("path", "foo/bar", cmake.path());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_ADD_C_MAKE_PATH_AND_APPLY_CHANGES_EXPECTED);
    assertEquals("path", "foo/bar", cmake.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    cmake = android.externalNativeBuild().cmake();
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testNdkBuild() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_NDK_BUILD);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testNdkBuildWithNewFilePath() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_NDK_BUILD_WITH_NEW_FILE_PATH);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testRemoveNdkBuildAndReset() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_NDK_BUILD_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_NDK_BUILD_AND_RESET);
  }

  @Test
  public void testRemoveNdkBuildAndApplyChanges() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_REMOVE_NDK_BUILD_AND_APPLY_CHANGES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");

    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);
  }

  @Test
  public void testAddNdkBuildPathAndReset() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertMissingProperty(ndkBuild.path());

    ndkBuild.path().setValue("foo/Android.mk");
    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    buildModel.resetState();
    assertMissingProperty(ndkBuild.path());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_RESET);
  }

  @Test
  public void testAddNdkBuildPathAndApplyChanges() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_APPLY_CHANGES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertMissingProperty(ndkBuild.path());

    ndkBuild.path().setValue("foo/Android.mk");
    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_ADD_NDK_BUILD_PATH_AND_APPLY_CHANGES_EXPECTED);

    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    ndkBuild = android.externalNativeBuild().ndkBuild();
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testSetConstructorToFunction() throws Exception {
    writeToBuildFile(EXTERNAL_NATIVE_BUILD_MODEL_SET_CONSTRUCTOR_TO_FUNCTION);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuildModel = android.externalNativeBuild().ndkBuild();
    assertEquals("path", "foo/Android.mk", ndkBuildModel.path());

    ndkBuildModel.path().setValue("foo/bar/file.txt");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, EXTERNAL_NATIVE_BUILD_MODEL_SET_CONSTRUCTOR_TO_FUNCTION_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    ndkBuildModel = android.externalNativeBuild().ndkBuild();
    verifyPropertyModel(ndkBuildModel.path(), STRING_TYPE, "foo/bar/file.txt", STRING, DERIVED, 0, "0");
  }
}
