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
package com.android.tools.idea.gradle.dsl.model.util;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_EMPTY3DOT5;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_EMPTY4DOT0;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_WITH_GOOGLE_ALREADY3DOT5;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_WITH_GOOGLE_ALREADY4DOT0;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_NAME3DOT5;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_NAME4DOT0;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_URL3DOT5;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_URL4DOT0;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModelExtensionKt;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenRepositoryModelImpl;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link GoogleMavenRepository}.
 */
public class GoogleMavenRepositoryTest extends GradleFileModelTestCase {

  @Test
  public void testHasGoogleMavenRepositoryEmpty() throws IOException {
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_EMPTY);
    assertFalse(getGradleBuildModel().repositories().hasGoogleMavenRepository());
  }

  @Test
  public void testHasGoogleMavenRepositoryName3dot5() throws IOException {
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_NAME3DOT5);
    assertTrue(getGradleBuildModel().repositories().hasGoogleMavenRepository());
  }

  @Test
  public void testHasGoogleMavenRepositoryName4dot0() throws IOException {
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_NAME4DOT0);
    assertTrue(getGradleBuildModel().repositories().hasGoogleMavenRepository());
  }

  @Test
  public void testHasGoogleMavenRepositoryUrl3dot5() throws IOException {
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_URL3DOT5);
    assertTrue(getGradleBuildModel().repositories().hasGoogleMavenRepository());
  }

  @Test
  public void testHasGoogleMavenRepositoryUrl4dot0() throws IOException {
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_HAS_GOOGLE_MAVEN_REPOSITORY_URL4DOT0);
    assertTrue(getGradleBuildModel().repositories().hasGoogleMavenRepository());
  }

  @Test
  public void testAddGoogleRepositoryEmpty3dot5() throws IOException {
    // Prepare repositories
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_EMPTY3DOT5);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).isEmpty();

    // add repository
    RepositoriesModelExtensionKt.addGoogleMavenRepository(GradleVersion.parse("3.5"));
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(MavenRepositoryModelImpl.class);
  }

  @Test
  public void testAddGoogleRepositoryEmpty4dot0() throws IOException {
    // Prepare repositories
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_EMPTY4DOT0);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).isEmpty();

    // add repository
    RepositoriesModelExtensionKt.addGoogleMavenRepository(GradleVersion.parse("4.0"));
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModelImpl.class);
  }

  @Test
  public void testAddGoogleRepositoryWithGoogleAlready3dot5() throws IOException {
    // Prepare repositories
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_WITH_GOOGLE_ALREADY3DOT5);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).hasSize(1);

    // add repository
    RepositoriesModelExtensionKt.addGoogleMavenRepository(GradleVersion.parse("3.5"));
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModelImpl.class);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);
  }

  @Test
  public void testAddGoogleRepositoryWithGoogleAlready4dot0() throws IOException {
    // Prepare repositories
    writeToBuildFile(GOOGLE_MAVEN_REPOSITORY_ADD_GOOGLE_REPOSITORY_WITH_GOOGLE_ALREADY4DOT0);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).hasSize(1);

    // add repository
    RepositoriesModelExtensionKt.addGoogleMavenRepository(GradleVersion.parse("4.0"));

    // Verify
    assertFalse(buildModel.isModified());
  }
}
