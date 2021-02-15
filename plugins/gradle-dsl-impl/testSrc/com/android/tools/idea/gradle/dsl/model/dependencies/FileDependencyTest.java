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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_ADD_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_ADD_FILE_DEPENDENCY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_INSERTION_ORDER;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_INSERTION_ORDER_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_PARSE_FILE_DEPENDENCIES_WITH_CLOSURE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_PARSE_MULTIPLE_FILE_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_PARSE_SINGLE_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_REMOVE_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_REMOVE_WHEN_MULTIPLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_MULTIPLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_SET_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_SET_FILE_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_UPDATE_SOME_OF_FILE_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.FILE_DEPENDENCY_UPDATE_SOME_OF_FILE_DEPENDENCIES_EXPECTED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link FileDependencyModelImpl}.
 */
public class FileDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParseSingleFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_PARSE_SINGLE_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testParseMultipleFileDependencies() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_PARSE_MULTIPLE_FILE_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(9);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib2.jar", fileDependencies.get(1).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(2).file().toString());
    assertEquals("lib4.jar", fileDependencies.get(3).file().toString());
    assertEquals("lib5.jar", fileDependencies.get(4).file().toString());
    assertEquals("lib6.jar", fileDependencies.get(5).file().toString());
    assertEquals("lib7.jar", fileDependencies.get(6).file().toString());
    assertEquals("lib8.jar", fileDependencies.get(7).file().toString());
    assertEquals("lib9.jar", fileDependencies.get(8).file().toString());
  }

  @Test
  public void testParseFileDependenciesWithClosure() throws IOException {
    assumeTrue("files dependency doesn't support a configuration closure in KotlinScript", !isKotlinScript()); // TODO(b/155080108)
    writeToBuildFile(FILE_DEPENDENCY_PARSE_FILE_DEPENDENCIES_WITH_CLOSURE);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib2.jar", fileDependencies.get(1).file().toString());
  }

  @Test
  public void testSetConfigurationWhenSingle() throws Exception {
    writeToBuildFile(FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> files = buildModel.dependencies().files();
    assertSize(4, files);

    assertThat(files.get(0).configurationName()).isEqualTo("test");
    files.get(0).setConfigurationName("androidTest");
    assertThat(files.get(0).configurationName()).isEqualTo("androidTest");

    assertThat(files.get(1).configurationName()).isEqualTo("compile");
    files.get(1).setConfigurationName("zapi");
    assertThat(files.get(1).configurationName()).isEqualTo("zapi");
    files.get(1).setConfigurationName("api"); // Try twice.
    assertThat(files.get(1).configurationName()).isEqualTo("api");

    assertThat(files.get(2).configurationName()).isEqualTo("api");
    files.get(2).setConfigurationName("zompile");
    assertThat(files.get(2).configurationName()).isEqualTo("zompile");
    files.get(2).setConfigurationName("compile"); // Try twice
    assertThat(files.get(2).configurationName()).isEqualTo("compile");

    assertThat(files.get(3).configurationName()).isEqualTo("testCompile");
    files.get(3).setConfigurationName("testImplementation");
    assertThat(files.get(3).configurationName()).isEqualTo("testImplementation");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE_EXPECTED);

    files = buildModel.dependencies().files();

    assertSize(4, files);
    assertThat(files.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(files.get(0).file().toString()).isEqualTo("libs");

    assertThat(files.get(1).configurationName()).isEqualTo("api");
    assertThat(files.get(1).file().toString()).isEqualTo("xyz");

    assertThat(files.get(2).configurationName()).isEqualTo("compile");
    assertThat(files.get(2).file().toString()).isEqualTo("klm");

    assertThat(files.get(3).configurationName()).isEqualTo("testImplementation");
    assertThat(files.get(3).file().toString()).isEqualTo("a");
  }

  @Test
  public void testSetConfigurationWhenMultiple() throws Exception {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(FILE_DEPENDENCY_SET_CONFIGURATION_WHEN_MULTIPLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> files = buildModel.dependencies().files();
    assertSize(10, files);
    assertThat(files.get(0).configurationName()).isEqualTo("testCompile");
    assertThat(files.get(0).file().toString()).isEqualTo("abc");

    assertThat(files.get(1).configurationName()).isEqualTo("testCompile");
    assertThat(files.get(1).file().toString()).isEqualTo("xyz");

    assertThat(files.get(2).configurationName()).isEqualTo("testCompile");
    assertThat(files.get(2).file().toString()).isEqualTo("a.jar");

    assertThat(files.get(3).configurationName()).isEqualTo("compile");
    assertThat(files.get(3).file().toString()).isEqualTo("klm");

    assertThat(files.get(4).configurationName()).isEqualTo("compile");
    assertThat(files.get(4).file().toString()).isEqualTo("libs");

    assertThat(files.get(5).configurationName()).isEqualTo("compile");
    assertThat(files.get(5).file().toString()).isEqualTo("pqr");

    assertThat(files.get(6).configurationName()).isEqualTo("compile");
    assertThat(files.get(6).file().toString()).isEqualTo("b.aar");

    assertThat(files.get(7).configurationName()).isEqualTo("compile");
    assertThat(files.get(7).file().toString()).isEqualTo("c.tmp");

    assertThat(files.get(8).configurationName()).isEqualTo("api");
    assertThat(files.get(8).file().toString()).isEqualTo("A");

    assertThat(files.get(9).configurationName()).isEqualTo("api");
    assertThat(files.get(9).file().toString()).isEqualTo("B");

    {
      files.get(0).setConfigurationName("androidTest1");
      files.get(0).setConfigurationName("androidTest");
      files.get(2).setConfigurationName("androidTest2");
      files.get(2).setConfigurationName("androidTest");
      List<FileDependencyModel> updatedFiles = buildModel.dependencies().files();
      assertSize(10, updatedFiles);

      assertThat(updatedFiles.get(0).configurationName()).isEqualTo("androidTest");
      assertThat(updatedFiles.get(0).file().toString()).isEqualTo("abc");

      // Note: The renamed element becomes the first in the group.
      assertThat(updatedFiles.get(1).configurationName()).isEqualTo("androidTest");
      assertThat(updatedFiles.get(1).file().toString()).isEqualTo("a.jar");

      assertThat(updatedFiles.get(2).configurationName()).isEqualTo("testCompile");
      assertThat(updatedFiles.get(2).file().toString()).isEqualTo("xyz");
    }

    {
      // Rename both elements of the same group and rename some of them twice.
      files.get(3).setConfigurationName("zapi");
      files.get(3).setConfigurationName("api");
      files.get(5).setConfigurationName("zimplementation");
      files.get(5).setConfigurationName("implementation");
      List<FileDependencyModel> updatedFiles = buildModel.dependencies().files();
      assertSize(10, updatedFiles);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedFiles.get(3).configurationName()).isEqualTo("api");
      assertThat(updatedFiles.get(3).file().toString()).isEqualTo("klm");

      assertThat(updatedFiles.get(4).configurationName()).isEqualTo("implementation");
      assertThat(updatedFiles.get(4).file().toString()).isEqualTo("pqr");

      assertThat(updatedFiles.get(5).configurationName()).isEqualTo("compile");
      assertThat(updatedFiles.get(5).file().toString()).isEqualTo("libs");

      assertThat(updatedFiles.get(6).configurationName()).isEqualTo("compile");
      assertThat(updatedFiles.get(6).file().toString()).isEqualTo("b.aar");

      assertThat(updatedFiles.get(7).configurationName()).isEqualTo("compile");
      assertThat(updatedFiles.get(7).file().toString()).isEqualTo("c.tmp");
    }

    {
      files.get(8).setConfigurationName("implementation1");
      files.get(8).setConfigurationName("implementation");
      List<FileDependencyModel> updatedFiles = buildModel.dependencies().files();
      assertSize(10, updatedFiles);

      assertThat(updatedFiles.get(8).configurationName()).isEqualTo("implementation");
      assertThat(updatedFiles.get(8).file().toString()).isEqualTo("A");

      assertThat(updatedFiles.get(9).configurationName()).isEqualTo("api");
      assertThat(updatedFiles.get(9).file().toString()).isEqualTo("B");
    }

    applyChangesAndReparse(buildModel);

    files = buildModel.dependencies().files();
    assertSize(10, files);

    assertThat(files.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(files.get(0).file().toString()).isEqualTo("abc");

    // Note: The renamed element becomes the first in the group.
    assertThat(files.get(1).configurationName()).isEqualTo("androidTest");
    assertThat(files.get(1).file().toString()).isEqualTo("a.jar");

    assertThat(files.get(2).configurationName()).isEqualTo("testCompile");
    assertThat(files.get(2).file().toString()).isEqualTo("xyz");

    assertThat(files.get(3).configurationName()).isEqualTo("api");
    assertThat(files.get(3).file().toString()).isEqualTo("klm");

    assertThat(files.get(4).configurationName()).isEqualTo("implementation");
    assertThat(files.get(4).file().toString()).isEqualTo("pqr");

    assertThat(files.get(5).configurationName()).isEqualTo("compile");
    assertThat(files.get(5).file().toString()).isEqualTo("libs");

    assertThat(files.get(6).configurationName()).isEqualTo("compile");
    assertThat(files.get(6).file().toString()).isEqualTo("b.aar");

    assertThat(files.get(7).configurationName()).isEqualTo("compile");
    assertThat(files.get(7).file().toString()).isEqualTo("c.tmp");

    assertThat(files.get(8).configurationName()).isEqualTo("implementation");
    assertThat(files.get(8).file().toString()).isEqualTo("A");

    assertThat(files.get(9).configurationName()).isEqualTo("api");
    assertThat(files.get(9).file().toString()).isEqualTo("B");
  }

  @Test
  public void testSetFile() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_SET_FILE);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel fileDependency = fileDependencies.get(0);
    assertEquals("lib1.jar", fileDependency.file().toString());

    fileDependency.file().setValue("lib2.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, FILE_DEPENDENCY_SET_FILE_EXPECTED);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib2.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testUpdateSomeOfFileDependencies() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_UPDATE_SOME_OF_FILE_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(6);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());

    FileDependencyModel fileDependency = fileDependencies.get(1);
    assertEquals("lib2.jar", fileDependency.file().toString());
    fileDependency.file().setValue("lib3.jar");

    fileDependency = fileDependencies.get(4);
    assertEquals("lib5.jar", fileDependency.file().toString());
    fileDependency.file().setValue("lib5.aar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, FILE_DEPENDENCY_UPDATE_SOME_OF_FILE_DEPENDENCIES_EXPECTED);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(6);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(1).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(2).file().toString());
    assertEquals("lib4.jar", fileDependencies.get(3).file().toString());
    assertEquals("lib5.aar", fileDependencies.get(4).file().toString());
    assertEquals("lib6.jar", fileDependencies.get(5).file().toString());
  }

  @Test
  public void testAddFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_ADD_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    DependenciesModel dependencies = buildModel.dependencies();
    assertThat(dependencies.files()).isEmpty();

    dependencies.addFile("compile", "lib1.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, FILE_DEPENDENCY_ADD_FILE_DEPENDENCY_EXPECTED);

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testRemoveFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_REMOVE_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel file = fileDependencies.get(0);
    assertEquals("lib1.jar", file.file().toString());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    assertThat(buildModel.dependencies().files()).isEmpty();
  }

  @Test
  public void testRemoveOneOfFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());

    FileDependencyModel file = fileDependencies.get(1);
    assertEquals("lib2.jar", file.file().toString());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY_EXPECTED);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testRemoveWhenMultiple() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(FILE_DEPENDENCY_REMOVE_WHEN_MULTIPLE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(6);

    assertEquals("lib2.jar", fileDependencies.get(1).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(2).file().toString());
    assertEquals("lib6.jar", fileDependencies.get(5).file().toString());

    dependencies.remove(fileDependencies.get(5));
    dependencies.remove(fileDependencies.get(2));
    dependencies.remove(fileDependencies.get(1));

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(3);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib4.jar", fileDependencies.get(1).file().toString());
    assertEquals("lib5.jar", fileDependencies.get(2).file().toString());
  }

  @Test
  public void testInsertionOrder() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_INSERTION_ORDER);

    GradleBuildModel buildModel = getGradleBuildModel();

    buildModel.dependencies().addFile("api", "a.jar");
    buildModel.dependencies().addFile("feature", "b.jar");
    buildModel.dependencies().addFile("testCompile", "c.jar");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    verifyFileContents(myBuildFile, FILE_DEPENDENCY_INSERTION_ORDER_EXPECTED);
  }
}
