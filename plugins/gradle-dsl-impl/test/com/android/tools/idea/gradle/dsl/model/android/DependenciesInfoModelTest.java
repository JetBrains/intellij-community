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

import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_ADD_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_ADD_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_EDIT_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_EDIT_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_PARSE;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_REMOVE_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.DEPENDENCIES_INFO_MODEL_REMOVE_AND_APPLY_EXPECTED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.DependenciesInfoModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.IOException;
import org.junit.Test;

public class DependenciesInfoModelTest extends GradleFileModelTestCase {
  @Test
  public void testParse() throws IOException {
    writeToBuildFile(DEPENDENCIES_INFO_MODEL_PARSE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DependenciesInfoModel dependenciesInfo = android.dependenciesInfo();
    assertEquals("includeInApk", Boolean.FALSE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.TRUE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testAddAndApply() throws IOException {
    writeToBuildFile(DEPENDENCIES_INFO_MODEL_ADD_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    dependenciesInfo.includeInApk().setValue(true);
    dependenciesInfo.includeInBundle().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEPENDENCIES_INFO_MODEL_ADD_AND_APPLY_EXPECTED);

    dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testEditAndApply() throws IOException {
    writeToBuildFile(DEPENDENCIES_INFO_MODEL_EDIT_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    dependenciesInfo.includeInApk().setValue(true);
    dependenciesInfo.includeInBundle().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEPENDENCIES_INFO_MODEL_EDIT_AND_APPLY_EXPECTED);

    dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
  }

  @Test
  public void testRemoveAndApply() throws IOException {
    writeToBuildFile(DEPENDENCIES_INFO_MODEL_REMOVE_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesInfoModel dependenciesInfo = buildModel.android().dependenciesInfo();
    assertEquals("includeInApk", Boolean.TRUE, dependenciesInfo.includeInApk());
    assertEquals("includeInBundle", Boolean.FALSE, dependenciesInfo.includeInBundle());
    dependenciesInfo.includeInApk().delete();
    dependenciesInfo.includeInBundle().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEPENDENCIES_INFO_MODEL_REMOVE_AND_APPLY_EXPECTED);
  }
}
