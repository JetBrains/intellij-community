/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.DependenciesInfoModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;


public class DependenciesInfoModelTest extends GradleFileModelTestCase {
  @Test
  public void testParse() throws IOException {
    writeToBuildFile(TestFile.PARSE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DependenciesInfoModel dependenciesInfo = android.dependenciesInfo();
    assertEquals("includeInApk", Boolean.FALSE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.TRUE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testAddAndApply() throws IOException {
    writeToBuildFile(TestFile.ADD_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    dependenciesInfo.includeInApk().setValue(true);
    dependenciesInfo.includeInBundle().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EXPECTED);

    dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testEditAndApply() throws IOException {
    writeToBuildFile(TestFile.EDIT_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    dependenciesInfo.includeInApk().setValue(true);
    dependenciesInfo.includeInBundle().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_AND_APPLY_EXPECTED);

    dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testRemoveAndApply() throws IOException {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
    dependenciesInfo.includeInApk().delete();
    dependenciesInfo.includeInBundle().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_EXPECTED);
  }

  enum TestFile implements TestFileName {
    PARSE("parse"),
    ADD_AND_APPLY("addAndApply"),
    ADD_AND_APPLY_EXPECTED("addAndApplyExpected"),
    EDIT_AND_APPLY("editAndApply"),
    EDIT_AND_APPLY_EXPECTED("editAndApplyExpected"),
    REMOVE_AND_APPLY("removeAndApply"),
    REMOVE_AND_APPLY_EXPECTED("removeAndApplyExpected"),
    ;

    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/dependenciesInfoModel/" + path, extension);
    }
  }
}
