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
package com.android.tools.idea.gradle.dsl.model.repositories;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_FROM_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_PRESENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_PRESENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_WITH_WITH;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_JCENTER_REPOSITORY_BY_URL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_MAVEN_CENTRAL_REPOSITORY_BY_URL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_REPOSITORY_BY_URL_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_MULTIPLE_LOCAL_REPOS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_CUSTOM_MAVEN_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_DIR_LIST_ARGUMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_SINGLE_DIR_ARGUMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_GOOGLE_DEFAULT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_J_CENTER_CUSTOM_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_J_CENTER_DEFAULT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_MULTIPLE_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_SINGLE_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_CREDENTIALS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_PARSE_MULTIPLE_REPOSITORIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_FOR_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_FOR_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_CREDENTIALS_IN_MAVEN;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_CREDENTIALS_IN_MAVEN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_METHOD_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.JCenterRepositoryModel.JCENTER_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.dsl.model.repositories.MavenCentralRepositoryModel.MAVEN_CENTRAL_DEFAULT_REPO_URL;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.MavenCredentialsModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link RepositoriesModelImpl}.
 */
public class RepositoriesModelTest extends GradleFileModelTestCase {


  @NotNull private static final String TEST_DIR = "hello/i/am/a/dir";
  @NotNull private static final String OTHER_TEST_DIR = "/this/is/also/a/dir";

  @Test
  public void testParseJCenterDefaultRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_J_CENTER_DEFAULT_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testParseJCenterCustomRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_J_CENTER_CUSTOM_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(JCenterRepositoryModel.class);
    JCenterRepositoryModel repository = (JCenterRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://jcenter.bintray.com/", repository.url().toString());
  }

  @Test
  public void testParseMavenCentralRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertEquals("name", "MavenRepo", repository.name().toString());
    assertNull("url", repository.name().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertNull("url", repository.url().getPsiElement());
  }

  @Test
  public void testParseMavenCentralRepositoryWithMultipleArtifactUrls() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_MULTIPLE_ARTIFACT_URLS);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "nonDefaultName", repository.name().toString());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://www.mycompany.com/artifacts1", "http://www.mycompany.com/artifacts2"),
                 repository.artifactUrls());
  }

  @Test
  public void testParseMavenCentralRepositoryWithSingleArtifactUrls() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_SINGLE_ARTIFACT_URLS);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "MavenRepo", repository.name().toString());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertEquals("artifactUrls", "http://www.mycompany.com/artifacts", repository.artifactUrls());
  }

  @Test
  public void testParseCustomMavenRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_CUSTOM_MAVEN_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "myRepoName", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().toString());
  }

  @Test
  public void testParseMavenRepositoryWithArtifactUrls() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_ARTIFACT_URLS);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo2.mycompany.com/maven2", repository.url().toString());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://repo.mycompany.com/jars", "http://repo.mycompany.com/jars2"),
                 repository.artifactUrls());
  }

  @Test
  public void testParseMavenRepositoryWithCredentials() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_CREDENTIALS);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().toString());
    assertMissingProperty(repository.artifactUrls());

    MavenCredentialsModel credentials = repository.credentials();
    assertNotNull(credentials);
    assertEquals("username", "user", credentials.username());
    assertEquals("password", "password123", credentials.password());
  }

  @Test
  public void testParseFlatDirRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().toString());
    assertEquals("dirs", ImmutableList.of("lib1", "lib2"), repository.dirs());
  }

  @Test
  public void testParseFlatDirRepositoryWithSingleDirArgument() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_SINGLE_DIR_ARGUMENT);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "libs", repository.name().toString());
    assertEquals("dirs", "libs", repository.dirs());
  }

  @Test
  public void testParseFlatDirRepositoryWithDirListArgument() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_DIR_LIST_ARGUMENT);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().toString());
    assertEquals("dirs", ImmutableList.of("libs1", "libs2"), repository.dirs());
  }

  @Test
  public void testParseMultipleRepositories() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_MULTIPLE_REPOSITORIES);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));

    RepositoryModel mavenCentral = repositories.get(1);
    assertThat(mavenCentral).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel mavenCentralRepository = (MavenCentralRepositoryModel)mavenCentral;
    assertEquals("name", "MavenRepo", mavenCentralRepository.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", mavenCentralRepository.url());
  }

  @Test
  public void testParseGoogleDefaultRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_PARSE_GOOGLE_DEFAULT_REPOSITORY);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCall() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByMethodCall(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EXPECTED);

    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCallEmpty() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    verifyAddGoogleRepositoryByMethodCall(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY_EXPECTED);
  }

  @Test
  public void testAddGoogleRepositoryToEmptyBuildscript() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT_EXPECTED);

    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.buildscript().repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCallPresent() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_PRESENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertFalse(buildModel.isModified());
  }

  @Test
  public void testAddGoogleRepositoryByUrl() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByUrl(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EXPECTED);
    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByUrlEmpty() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_REPOSITORY_BY_URL_EMPTY);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assumeTrue(repositories.isEmpty());
    verifyAddGoogleRepositoryByUrl(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EMPTY_EXPECTED);
  }

  @Test
  public void testAddMavenCentralRepositoryByUrlEmpty() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_REPOSITORY_BY_URL_EMPTY);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assumeTrue(repositories.isEmpty());
    int prevSize = repositories.size();

    repositoriesModel.addMavenRepositoryByUrl(MAVEN_CENTRAL_DEFAULT_REPO_URL, "MavenRepo");
    assertTrue(buildModel.isModified());

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_MAVEN_CENTRAL_REPOSITORY_BY_URL_EMPTY_EXPECTED);

    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyMavenRepositoryModel(repositories.get(prevSize), MAVEN_CENTRAL_DEFAULT_REPO_URL, "MavenRepo", RepositoryType.MAVEN_CENTRAL);
  }

  @Test
  public void testAddJCenterRepositoryByUrlEmpty() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_REPOSITORY_BY_URL_EMPTY);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assumeTrue(repositories.isEmpty());

    int prevSize = repositories.size();

    repositoriesModel.addMavenRepositoryByUrl(JCENTER_DEFAULT_REPO_URL, "BintrayJCenter2");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_JCENTER_REPOSITORY_BY_URL_EMPTY_EXPECTED);

    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyMavenRepositoryModel(repositories.get(prevSize), JCENTER_DEFAULT_REPO_URL, "BintrayJCenter2", RepositoryType.JCENTER_DEFAULT);
  }

  @Test
  public void testAddGoogleRepositoryByUrlPresent() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_PRESENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleMavenRepositoryModel(repositories.get(0));

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertFalse(buildModel.isModified());
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_PRESENT);
  }

  private static void verifyGoogleDefaultRepositoryModel(RepositoryModel google) {
    assertThat(google).isInstanceOf(GoogleDefaultRepositoryModelImpl.class);
    GoogleDefaultRepositoryModelImpl googleRepository = (GoogleDefaultRepositoryModelImpl)google;
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, googleRepository.name());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, googleRepository.url());
  }

  private static void verifyJCenterDefaultRepositoryModel(RepositoryModel jcenter) {
    assertThat(jcenter).isInstanceOf(UrlBasedRepositoryModel.class);
    UrlBasedRepositoryModel jCenterRepository = (UrlBasedRepositoryModel)jcenter;
    assertNull("name", jCenterRepository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", jCenterRepository.name().toString());
    assertNull("url", jCenterRepository.url().getPsiElement());
    assertEquals("url", "https://jcenter.bintray.com/", jCenterRepository.url().toString());
  }

  private void verifyAddGoogleRepositoryByMethodCall(TestFileName expected) throws IOException {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, expected);

    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleDefaultRepositoryModel(repositories.get(prevSize));
  }

  private void verifyAddGoogleRepositoryByUrl(TestFileName expected) throws IOException {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, expected);

    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleMavenRepositoryModel(repositories.get(prevSize));
  }

  private static void verifyMavenRepositoryModel(RepositoryModel repository, String url, String name, RepositoryType type) {
    assertThat(repository).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl mavenRepository = (MavenRepositoryModelImpl)repository;
    assertNotNull("url Psi", mavenRepository.url().getPsiElement());
    assertEquals("url", url, mavenRepository.url().toString());
    assertNotNull("name Psi", mavenRepository.name().getPsiElement());
    assertEquals("name", name, mavenRepository.name().toString());
    assertEquals("type", type, mavenRepository.getType());
  }

  private static void verifyGoogleMavenRepositoryModel(RepositoryModel repository) {
    verifyMavenRepositoryModel(repository, GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME, RepositoryType.GOOGLE_DEFAULT);
  }

  @Test
  public void testAddFlatRepository() throws IOException {
    writeToBuildFile("repositories {\n}");

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addFlatDirRepository("/usr/local/repo");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_EXPECTED);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatModel.dirs().toList()).hasSize(1);
    assertEquals("/usr/local/repo", flatModel.dirs().toList().get(0).toString());
    assertEquals(RepositoryType.FLAT_DIR, flatModel.getType());
  }

  @Test
  public void testAddFlatRepositoryFromEmpty() throws IOException {
    writeToBuildFile("");

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addFlatDirRepository("/usr/local/repo");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_FROM_EMPTY_EXPECTED);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatModel.dirs().toList()).hasSize(1);
    assertEquals("/usr/local/repo", flatModel.dirs().toList().get(0).toString());
    assertEquals(RepositoryType.FLAT_DIR, flatModel.getType());
  }

  @Test
  public void testAddToExistingFlatRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addFlatDirRepository(OTHER_TEST_DIR);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY_EXPECTED);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatDirRepositoryModel = (FlatDirRepositoryModel)repos.get(0);

    List<GradlePropertyModel> dirs = flatDirRepositoryModel.dirs().toList();
    dirs.sort(Comparator.comparing(e -> e.resolve().toString()));

    assertEquals(OTHER_TEST_DIR, dirs.get(0).toString());
    assertEquals(TEST_DIR, dirs.get(1).toString());
  }

  @Test
  public void testAddDuplicateToExistingFlatRepository() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addFlatDirRepository(TEST_DIR);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY_EXPECTED);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatDirRepositoryModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatDirRepositoryModel.dirs().toList()).hasSize(2);
    assertEquals(TEST_DIR, flatDirRepositoryModel.dirs().toList().get(0).toString());
    assertEquals(TEST_DIR, flatDirRepositoryModel.dirs().toList().get(1).toString());
  }

  @Test
  public void testSetNameForMethodCall() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    repositories.get(0).name().setValue("hello");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL_EXPECTED);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(JCenterRepositoryModel.class);
    assertThat(repositories.get(0).name().toString()).isEqualTo("hello");
  }

  @Test
  public void testSetUrlForMethodCall() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));

    ((UrlBasedRepositoryModel)repositories.get(0)).url().setValue("good.url");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL_EXPECTED);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertSize(1, repositories);
    assertThat(repositories.get(0)).isInstanceOf(JCenterRepositoryModel.class);
    assertThat(((JCenterRepositoryModel)repositories.get(0)).url().toString()).isEqualTo("good.url");
  }

  @Test
  public void testSetArtifactUrlsForMethodCall() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_SET_ARTIFACT_URLS_FOR_METHOD_CALL);
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    verifyJCenterDefaultRepositoryModel(repositoryModel);

    ((MavenRepositoryModelImpl) repositoryModel).artifactUrls().addListValue().setValue("some.url");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_SET_ARTIFACT_URLS_FOR_METHOD_CALL_EXPECTED);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertSize(1, repositories);
    assertThat(repositories.get(0)).isInstanceOf(JCenterRepositoryModel.class);
    verifyListProperty(((JCenterRepositoryModel) (repositories.get(0))).artifactUrls(), ImmutableList.of("some.url"));
  }

  @Test
  public void testSetArtifactUrlsInMaven() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addMavenRepositoryByUrl("good.url", "Good Name");
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);

    ((MavenRepositoryModelImpl)repositories.get(1)).artifactUrls().addListValue().setValue("nice.url");
    ((MavenRepositoryModelImpl)repositories.get(1)).artifactUrls().addListValue().setValue("other.nice.url");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN_EXPECTED);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl model = (MavenRepositoryModelImpl)repositories.get(1);
    assertThat(model.name().toString()).isEqualTo("Good Name");
    assertThat(model.url().toString()).isEqualTo("good.url");
    verifyListProperty(model.artifactUrls(), "repositories.maven.artifactUrls", ImmutableList.of("nice.url", "other.nice.url"));
  }

  @Test
  public void testSetCredentialsInMaven() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_SET_CREDENTIALS_IN_MAVEN);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addMavenRepositoryByUrl("good.url", "Good Name");
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);

    ((MavenRepositoryModelImpl)repositories.get(1)).credentials().username().setValue("joe.bloggs");
    ((MavenRepositoryModelImpl)repositories.get(1)).credentials().password().setValue("12345678");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_SET_CREDENTIALS_IN_MAVEN_EXPECTED);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl model = (MavenRepositoryModelImpl)repositories.get(1);
    assertThat(model.name().toString()).isEqualTo("Good Name");
    assertThat(model.url().toString()).isEqualTo("good.url");
    assertThat(model.credentials().username().toString()).isEqualTo("joe.bloggs");
    assertThat(model.credentials().password().toString()).isEqualTo("12345678");
  }

  @Test
  public void testMultipleLocalRepos() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_MULTIPLE_LOCAL_REPOS);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositoriesModels = repositoriesModel.repositories();
    assertThat(repositoriesModels).hasSize(3);

    RepositoryModel firstModel = repositoriesModels.get(0);
    verifyPropertyModel(firstModel.name(), "repositories.maven.name", "test2");
    assertEquals(RepositoryModel.RepositoryType.MAVEN, firstModel.getType());
    assertThat(firstModel).isInstanceOf(MavenRepositoryModelImpl.class);
    assertEquals("file:/some/other/repo", ((MavenRepositoryModelImpl)firstModel).url().toString());

    RepositoryModel secondModel = repositoriesModels.get(1);
    assertEquals(RepositoryModel.RepositoryType.MAVEN, secondModel.getType());
    assertThat(secondModel).isInstanceOf(MavenRepositoryModelImpl.class);
    assertEquals("file:/the/best/repo", ((MavenRepositoryModelImpl)secondModel).url().toString());

    RepositoryModel thirdModel = repositoriesModels.get(2);
    assertEquals(RepositoryModel.RepositoryType.MAVEN, thirdModel.getType());
    assertThat(thirdModel).isInstanceOf(MavenRepositoryModelImpl.class);
    assertEquals("file:/some/repo", ((MavenRepositoryModelImpl)thirdModel).url().toString());
  }
}
