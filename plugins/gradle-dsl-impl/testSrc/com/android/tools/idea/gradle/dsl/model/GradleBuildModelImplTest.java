/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_SINGLE_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_ALLPROJECTS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_BUILDSCRIPT_REPOSITORIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_BUILDSCRIPT_REPOSITORIES_EXPECTED;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link GradleBuildModelImpl}.
 */
public class GradleBuildModelImplTest extends GradleFileModelTestCase {
  @Test
  public void testRemoveRepositoriesSingleBlock() throws IOException {
    writeToBuildFile(GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_SINGLE_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");
  }

  @Test
  public void testRemoveRepositoriesMultipleBlocks() throws IOException {
    writeToBuildFile(GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");
  }

  @Test
  public void testRemoveRepositoriesWithBuildscriptRepositories() throws IOException {
    writeToBuildFile(GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_BUILDSCRIPT_REPOSITORIES);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    BuildScriptModel buildscript = getGradleBuildModel().buildscript();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);

    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    buildscript = buildModel.buildscript();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_BUILDSCRIPT_REPOSITORIES_EXPECTED);
  }

  @Test
  public void testRemoveRepositoriesWithAllprojectsBlock() throws IOException {
    writeToBuildFile(GRADLE_BUILD_MODEL_IMPL_REMOVE_REPOSITORIES_WITH_ALLPROJECTS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);

    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");
  }
}
