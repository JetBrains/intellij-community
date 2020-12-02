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
package com.android.tools.idea.gradle.dsl.model.build;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_ADD_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_ADD_DEPENDENCY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_NOT_VISIBLE_FROM_BUILDSCRIPT_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_NOT_VISIBLE_FROM_BUILDSCRIPT_BLOCK_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_PARSE_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_PARSE_REPOSITORIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_SINGLE_BLOCK;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_URL;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyTest.ExpectedArtifactDependency;
import com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link BuildScriptModelImpl}.
 */
public class BuildScriptModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseDependencies() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_PARSE_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testAddDependency() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_ADD_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildScriptModel buildScriptModel = buildModel.buildscript();
    DependenciesModel dependenciesModel = buildScriptModel.dependencies();

    assertFalse(hasPsiElement(buildScriptModel));
    assertFalse(hasPsiElement(dependenciesModel));
    assertThat(dependenciesModel.artifacts()).isEmpty();

    dependenciesModel.addArtifact("classpath", "com.android.tools.build:gradle:2.0.0-alpha2");

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, BUILD_SCRIPT_MODEL_ADD_DEPENDENCY_EXPECTED);

    assertFalse(buildModel.isModified());

    buildModel.reparse();
    buildScriptModel = buildModel.buildscript();
    dependenciesModel = buildScriptModel.dependencies();

    assertTrue(hasPsiElement(buildScriptModel));
    assertTrue(hasPsiElement(dependenciesModel));
    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testEditDependency() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    ArtifactDependencyModel actual = dependencies.get(0);
    expected.assertMatches(actual);

    actual.version().setValue("2.0.1");

    expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.1");
    expected.assertMatches(actual);

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY_EXPECTED);
    assertFalse(buildModel.isModified());

    buildModel.reparse();
    dependencies = buildModel.buildscript().dependencies().artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testParseRepositories() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_PARSE_REPOSITORIES);

    RepositoriesModel repositoriesModel = getGradleBuildModel().buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof UrlBasedRepositoryModel);
    UrlBasedRepositoryModel repository = (UrlBasedRepositoryModel)repositoryModel;
    assertEquals("name", "BintrayJCenter2", repository.name());
    assertEquals("url", "https://jcenter.bintray.com/", repository.url());

    repositoryModel = repositories.get(1);
    assertTrue(repositoryModel instanceof GoogleDefaultRepositoryModelImpl);
    GoogleDefaultRepositoryModelImpl googleRepository = (GoogleDefaultRepositoryModelImpl)repositoryModel;
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, googleRepository.name());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, googleRepository.url());
  }

  @Test
  public void testRemoveRepositoriesSingleBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_SINGLE_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    BuildScriptModel buildscript = buildModel.buildscript();
    List<RepositoryModel> repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildscript.removeRepositoriesBlocks();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(0);
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");
  }

  @Test
  public void testRemoveRepositoriesMultipleBlocks() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS);
    GradleBuildModel buildModel = getGradleBuildModel();
    BuildScriptModel buildscript = buildModel.buildscript();
    List<RepositoryModel> repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildscript.removeRepositoriesBlocks();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(0);
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");
  }

  @Test
  public void testExtPropertiesFromBuildscriptBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK);
    writeToSubModuleBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK_SUB);
    writeToSettingsFile(getSubModuleSettingsText());

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();

    verifyPropertyModel(buildModel.android().defaultConfig().applicationId(), STRING_TYPE, "boo", STRING, PropertyType.REGULAR, 1);
  }

  @Test
  public void testExtPropertiesNotVisibleFromBuildscriptBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_NOT_VISIBLE_FROM_BUILDSCRIPT_BLOCK);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ArtifactDependencyModel> artifacts = buildModel.buildscript().dependencies().artifacts();
    assertSize(1, artifacts);
    String propertyText;
    if (isGroovy()) {
      propertyText = "$VERSION";
    }
    else {
      propertyText = "${project.extra[\"VERSION\"]}";
    }
    verifyPropertyModel(artifacts.get(0).completeModel(), "buildscript.dependencies.classpath", "com.android.tools.build:gradle:" + propertyText);
  }

  @Test
  public void testAddExtVariableToBuildscriptBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_NOT_VISIBLE_FROM_BUILDSCRIPT_BLOCK);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ArtifactDependencyModel> artifacts = buildModel.buildscript().dependencies().artifacts();
    assertSize(1, artifacts);
    String propertyText;
    if (isGroovy()) {
      propertyText = "$VERSION";
    }
    else {
      propertyText = "${project.extra[\"VERSION\"]}";
    }
    verifyPropertyModel(artifacts.get(0).completeModel(), "buildscript.dependencies.classpath", "com.android.tools.build:gradle:" + propertyText);

    // Add the missing variable to the buildscript block.
    buildModel.buildscript().ext().findProperty("VERSION").setValue("2.1.2");
    // Add a new normal dependency that uses the VERSION property to ensure we don't resolve to the buildscript one.
    buildModel.android().defaultConfig().applicationId().setValue(new ReferenceTo("VERSION"));

    applyChangesAndReparse(buildModel);
    // TODO(b/148271448): this passes and both language syntaxes are legal, but (in some sense) only by accident: the
    //  KotlinScript writer writes ${extra[""]} within a dependencies { } block to pick up a buildscript property, but dependencies is
    //  itself ExtensionAware.  However: there is no `extra` extension function on DependencyHandlerScope, only `ext`, which means that
    //  the `extra` reference is resolved on the buildscript block rather than the dependencies block.
    verifyFileContents(myBuildFile, BUILD_SCRIPT_MODEL_EXT_PROPERTIES_NOT_VISIBLE_FROM_BUILDSCRIPT_BLOCK_EXPECTED);

    artifacts = buildModel.buildscript().dependencies().artifacts();
    assertSize(1, artifacts);
    verifyPropertyModel(artifacts.get(0).completeModel(), "buildscript.dependencies.classpath", "com.android.tools.build:gradle:2.1.2");

    ResolvedPropertyModel applicationId = buildModel.android().defaultConfig().applicationId();
    assertEquals("applicationId", "3.2.0", applicationId);
  }
}
