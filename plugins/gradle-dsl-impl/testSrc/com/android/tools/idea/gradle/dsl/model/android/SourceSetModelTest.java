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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_ADD_AND_APPLY_BLOCK_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_ADD_AND_APPLY_BLOCK_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_REMOVE_AND_APPLY_BLOCK_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_ADD_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_ADD_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_ADD_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_IN_SOURCE_SET_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_OVERRIDE_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_REMOVE_AND_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_REMOVE_AND_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_SET_MODEL_SET_ROOT_STATEMENTS;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link SourceSetModel}.
 */
public class SourceSetModelTest extends GradleFileModelTestCase {
  @Test
  public void testSetRootInSourceSetBlock() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_IN_SOURCE_SET_BLOCK);
    verifySourceSetRoot(getGradleBuildModel(), "source");
  }

  @Test
  public void testSetRootStatements() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_STATEMENTS);
    verifySourceSetRoot(getGradleBuildModel(), "source");
  }

  @Test
  public void testSetRootOverrideStatements() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_OVERRIDE_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "override");
  }

  @Test
  public void testSetRootEditAndReset() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    setRootOf(sourceSets, "set1", "newRoot1");
    setRootOf(sourceSets, "set2", "newRoot2");
    setRootOf(sourceSets, "set3", "newRoot3");
    setRootOf(sourceSets, "set4", "newRoot4");
    verifySourceSetRoot(buildModel, "newRoot");

    buildModel.resetState();
    verifySourceSetRoot(buildModel, "source");
  }

  @Test
  public void testSetRootEditAndApply() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    setRootOf(sourceSets, "set1", "newRoot1");
    setRootOf(sourceSets, "set2", "newRoot2");
    setRootOf(sourceSets, "set3", "newRoot3");
    setRootOf(sourceSets, "set4", "newRoot4");
    verifySourceSetRoot(buildModel, "newRoot");

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, SOURCE_SET_MODEL_SET_ROOT_EDIT_AND_APPLY_EXPECTED);

    verifySourceSetRoot(buildModel, "newRoot");
    buildModel.reparse();
    verifySourceSetRoot(buildModel, "newRoot");
  }

  @Test
  public void testSetRootAddAndReset() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_ADD_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertMissingProperty("root", sourceSet.root());

    sourceSet.root().setValue("source");
    assertEquals("root", "source", sourceSet.root());

    buildModel.resetState();
    assertMissingProperty("root", sourceSet.root());
  }

  @Test
  public void testSetRootAddAndApply() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_ADD_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertMissingProperty("root", sourceSet.root());

    sourceSet.root().setValue("source");
    assertEquals("root", "source", sourceSet.root());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, SOURCE_SET_MODEL_SET_ROOT_ADD_AND_APPLY_EXPECTED);

    assertEquals("root", "source", sourceSet.root());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertEquals("root", "source", sourceSet.root());
  }

  @Test
  public void testSetRootRemoveAndReset() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_REMOVE_AND_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    for (SourceSetModel sourceSet : android.sourceSets()) {
      sourceSet.root().delete();
    }

    for (SourceSetModel sourceSet : android.sourceSets()) {
      assertMissingProperty("root", sourceSet.root());
    }

    buildModel.resetState();
    verifySourceSetRoot(buildModel, "source");
  }

  @Test
  public void testSetRootRemoveAndApply() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_REMOVE_AND_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    for (SourceSetModel sourceSet : android.sourceSets()) {
      sourceSet.root().delete();
    }

    for (SourceSetModel sourceSet : android.sourceSets()) {
      assertMissingProperty("root", sourceSet.root());
    }

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // the whole android block is deleted from the file.
    assertThat(android.sourceSets()).isEmpty();
  }

  private static void setRootOf(@NotNull List<SourceSetModel> sourceSetModels, @NotNull String set, @NotNull String root) {
    sourceSetModels.stream().filter(ss -> ss.name().equals(set)).findFirst().ifPresent(s -> s.root().setValue(root));
  }

  private static void verifySourceSetRoot(@NotNull GradleBuildModel buildModel, @NotNull String rootPrefix) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(4);
    for (int i = 1; i <= sourceSets.size(); i++) {
      verifySourceSetRoot(sourceSets, rootPrefix, i);
    }
  }

  private static void verifySourceSetRoot(@NotNull List<SourceSetModel> sourceSets, @NotNull String rootPrefix, int i) {
    assertTrue(sourceSets.stream().anyMatch(ss -> ss.name().equals("set" + i)));
    sourceSets.stream().filter(ss -> ss.name().equals("set" + i)).findFirst()
      .ifPresent(set -> assertEquals("root", rootPrefix + i, set.root()));
  }

  @Test
  public void testAddAndApplyBlockElements() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_ADD_AND_APPLY_BLOCK_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    sourceSet.aidl().srcDirs().addListValue().setValue("aidlSource");
    sourceSet.assets().srcDirs().addListValue().setValue("assetsSource");
    sourceSet.java().srcDirs().addListValue().setValue("javaSource");
    sourceSet.jni().srcDirs().addListValue().setValue("jniSource");
    sourceSet.jniLibs().srcDirs().addListValue().setValue("jniLibsSource");
    sourceSet.manifest().srcFile().addListValue().setValue("manifestSource.xml");
    sourceSet.mlModels().srcDirs().addListValue().setValue("mlModelsSource");
    sourceSet.renderscript().srcDirs().addListValue().setValue("renderscriptSource");
    sourceSet.res().srcDirs().addListValue().setValue("resSource");
    sourceSet.resources().srcDirs().addListValue().setValue("resourcesSource");
    sourceSet.shaders().srcDirs().addListValue().setValue("shadersSource");
    verifySourceSet(sourceSet, false /*to verify that the block elements are still not saved to the file*/);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_SET_MODEL_ADD_AND_APPLY_BLOCK_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);
    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());
    verifySourceSet(sourceSet, true /*the elements are saved to file and the parser was able to find them all*/);
  }

  private static void verifySourceSet(SourceSetModel sourceSet, boolean savedToFile) {
    SourceDirectoryModel aidl = sourceSet.aidl();
    assertEquals("name", "aidl", aidl.name());
    assertThat(aidl.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(aidl));

    SourceDirectoryModel assets = sourceSet.assets();
    assertEquals("name", "assets", assets.name());
    assertThat(assets.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(aidl));

    SourceDirectoryModel java = sourceSet.java();
    assertEquals("name", "java", java.name());
    assertThat(java.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(java));

    SourceDirectoryModel jni = sourceSet.jni();
    assertEquals("name", "jni", jni.name());
    assertThat(jni.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(java));

    SourceDirectoryModel jniLibs = sourceSet.jniLibs();
    assertEquals("name", "jniLibs", jniLibs.name());
    assertThat(jniLibs.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(jniLibs));

    SourceFileModel manifest = sourceSet.manifest();
    assertEquals("name", "manifest", manifest.name());
    assertNotNull(manifest.srcFile());
    assertEquals(savedToFile, hasPsiElement(manifest));

    SourceDirectoryModel mlModels = sourceSet.mlModels();
    assertEquals("name", "mlModels", mlModels.name());
    assertThat(mlModels.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(mlModels));

    SourceDirectoryModel renderscript = sourceSet.renderscript();
    assertEquals("name", "renderscript", renderscript.name());
    assertThat(renderscript.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(renderscript));

    SourceDirectoryModel res = sourceSet.res();
    assertEquals("name", "res", res.name());
    assertThat(res.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(res));

    SourceDirectoryModel resources = sourceSet.resources();
    assertEquals("name", "resources", resources.name());
    assertThat(resources.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(resources));

    SourceDirectoryModel shaders = sourceSet.shaders();
    assertEquals("name", "shaders", shaders.name());
    assertThat(shaders.srcDirs().toList()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(shaders));
  }

  @Test
  public void testRemoveAndApplyBlockElements() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_REMOVE_AND_APPLY_BLOCK_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());
    verifySourceSet(sourceSet, true /*elements are present in the file and the parser was able to find them all*/);

    sourceSet.removeAidl();
    sourceSet.removeAssets();
    sourceSet.removeJava();
    sourceSet.removeJni();
    sourceSet.removeJniLibs();
    sourceSet.removeManifest();
    sourceSet.removeMlModels();
    sourceSet.removeRenderscript();
    sourceSet.removeRes();
    sourceSet.removeResources();
    sourceSet.removeShaders();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertEmpty(android.sourceSets());
  }

  @Test
  public void testRenameSourceSet() throws Exception {
    writeToBuildFile(SOURCE_SET_MODEL_SET_ROOT_OVERRIDE_STATEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<SourceSetModel> sourceSets = buildModel.android().sourceSets();
    assertSize(4, sourceSets);

    SourceSetModel sourceSet = sourceSets.get(0);

    sourceSet.rename("awesomeNewSourceSet");

    assertEquals("awesomeNewSourceSet", sourceSet.name());

    applyChangesAndReparse(buildModel);

    sourceSet = buildModel.android().sourceSets().get(0);
    assertEquals("awesomeNewSourceSet", sourceSet.name());
  }
}
