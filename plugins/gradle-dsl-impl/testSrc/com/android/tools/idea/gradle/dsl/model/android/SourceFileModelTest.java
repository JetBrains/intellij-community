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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_REMOVE_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_FILE_MODEL_SOURCE_FILE_REMOVE_AND_RESET;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/**
 * Tests for {@link SourceFileModel}.
 */
public class SourceFileModelTest extends GradleFileModelTestCase {
  @Test
  public void testSourceFile() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE);
    verifySourceFile(getGradleBuildModel(), "mainSource.xml");
  }

  @Test
  public void testSourceFileEditAndReset() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_RESET);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().setValue("otherSource.xml");
    verifySourceFile(buildModel, "otherSource.xml");

    buildModel.resetState();
    verifySourceFile(buildModel, "mainSource.xml");
  }

  @Test
  public void testSourceFileEditAndApply() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().setValue("otherSource.xml");
    verifySourceFile(buildModel, "otherSource.xml");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_FILE_MODEL_SOURCE_FILE_EDIT_AND_APPLY_EXPECTED);
    verifySourceFile(buildModel, "otherSource.xml");
  }

  @Test
  public void testSourceFileAddAndReset() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_RESET);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, null);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().setValue("mainSource.xml");
    verifySourceFile(buildModel, "mainSource.xml");

    buildModel.resetState();
    verifySourceFile(buildModel, null);
  }

  @Test
  public void testSourceFileAddAndApply() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, null);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().setValue("mainSource.xml");
    verifySourceFile(buildModel, "mainSource.xml");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_FILE_MODEL_SOURCE_FILE_ADD_AND_APPLY_EXPECTED);

    verifySourceFile(buildModel, "mainSource.xml");
  }

  @Test
  public void testSourceFileRemoveAndReset() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_REMOVE_AND_RESET);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().delete();
    verifySourceFile(buildModel, null);

    buildModel.resetState();
    verifySourceFile(buildModel, "mainSource.xml");
  }

  @Test
  public void testSourceFileRemoveAndApply() throws Exception {
    writeToBuildFile(SOURCE_FILE_MODEL_SOURCE_FILE_REMOVE_AND_APPLY);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().srcFile().delete();
    verifySourceFile(buildModel, null);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertThat(android.sourceSets()).isEmpty();
  }

  private static void verifySourceFile(@NotNull GradleBuildModel buildModel, @Nullable String srcFile) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    SourceFileModel manifest = sourceSet.manifest();
    assertNotNull(manifest);
    assertEquals("srcFile", srcFile, manifest.srcFile());
  }
}
