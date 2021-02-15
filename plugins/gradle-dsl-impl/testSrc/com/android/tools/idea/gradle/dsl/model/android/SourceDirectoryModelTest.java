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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_ADD_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_REMOVE_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_REPLACE_AND_APPLY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link SourceDirectoryModel}.
 */
public class SourceDirectoryModelTest extends GradleFileModelTestCase {
  @Test
  public void testSourceDirectoryEntries() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);
    verifySourceDirectoryEntries(getGradleBuildModel(), 1, 2);
  }

  @Test
  public void testSourceDirectoryEntriesAddAndReset() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().addListValue().setValue("javaSource3");
    java.includes().addListValue().setValue("javaInclude3");
    java.excludes().addListValue().setValue("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().addListValue().setValue("jniSource3");
    jni.includes().addListValue().setValue("jniInclude3");
    jni.excludes().addListValue().setValue("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 2, 3);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  @Test
  public void testSourceDirectoryEntriesAddAndApply() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().addListValue().setValue("javaSource3");
    java.includes().addListValue().setValue("javaInclude3");
    java.excludes().addListValue().setValue("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().addListValue().setValue("jniSource3");
    jni.includes().addListValue().setValue("jniInclude3");
    jni.excludes().addListValue().setValue("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 2, 3);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_ADD_AND_APPLY_EXPECTED);

    verifySourceDirectoryEntries(buildModel, 1, 2, 3);
  }

  @Test
  public void testSourceDirectoryEntriesRemoveAndReset() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().getListValue("javaSource2").delete();
    java.includes().getListValue("javaInclude2").delete();
    java.excludes().getListValue("javaExclude2").delete();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().getListValue("jniSource2").delete();
    jni.includes().getListValue("jniInclude2").delete();
    jni.excludes().getListValue("jniExclude2").delete();

    verifySourceDirectoryEntries(buildModel, 1);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  @Test
  public void testSourceDirectoryEntriesRemoveAndApply() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().getListValue("javaSource2").delete();
    java.includes().getListValue("javaInclude2").delete();
    java.excludes().getListValue("javaExclude2").delete();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().getListValue("jniSource2").delete();
    jni.includes().getListValue("jniInclude2").delete();
    jni.excludes().getListValue("jniExclude2").delete();

    verifySourceDirectoryEntries(buildModel, 1);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_REMOVE_AND_APPLY_EXPECTED);

    verifySourceDirectoryEntries(buildModel, 1);
  }

  @Test
  public void testSourceDirectoryEntriesRemoveAllAndReset() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().delete();
    java.includes().delete();
    java.excludes().delete();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().delete();
    jni.includes().delete();
    jni.excludes().delete();

    verifySourceDirectoryEntries(buildModel);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  @Test
  public void testSourceDirectoryEntriesRemoveAllAndApply() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().delete();
    java.includes().delete();
    java.excludes().delete();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().delete();
    jni.includes().delete();
    jni.excludes().delete();

    verifySourceDirectoryEntries(buildModel);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertEmpty(android.sourceSets());
  }

  @Test
  public void testSourceDirectoryEntriesReplaceAndReset() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().getListValue("javaSource2").setValue("javaSource3");
    java.includes().getListValue("javaInclude2").setValue("javaInclude3");
    java.excludes().getListValue("javaExclude2").setValue("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().getListValue("jniSource2").setValue("jniSource3");
    jni.includes().getListValue("jniInclude2").setValue("jniInclude3");
    jni.excludes().getListValue("jniExclude2").setValue("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 3);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  @Test
  public void testSourceDirectoryEntriesReplaceAndApply() throws Exception {
    writeToBuildFile(SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.srcDirs().getListValue("javaSource2").setValue("javaSource3");
    java.includes().getListValue("javaInclude2").setValue("javaInclude3");
    java.excludes().getListValue("javaExclude2").setValue("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.srcDirs().getListValue("jniSource2").setValue("jniSource3");
    jni.includes().getListValue("jniInclude2").setValue("jniInclude3");
    jni.excludes().getListValue("jniExclude2").setValue("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 3);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, SOURCE_DIRECTORY_MODEL_SOURCE_DIRECTORY_ENTRIES_REPLACE_AND_APPLY_EXPECTED);

    verifySourceDirectoryEntries(buildModel, 1, 3);
  }

  private static void verifySourceDirectoryEntries(@NotNull GradleBuildModel buildModel, int... entrySuffixes) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    verifySourceDirectory(sourceSet.java(), "java", entrySuffixes);
    verifySourceDirectory(sourceSet.jni(), "jni", entrySuffixes);
  }

  private static void verifySourceDirectory(@NotNull SourceDirectoryModel sourceDirectory, @NotNull String name, int... entrySuffixes) {
    assertEquals("name", name, sourceDirectory.name());
    ResolvedPropertyModel srcDirs = sourceDirectory.srcDirs();
    ResolvedPropertyModel includes = sourceDirectory.includes();
    ResolvedPropertyModel excludes = sourceDirectory.excludes();

    if (entrySuffixes.length == 0) {
      assertMissingProperty(srcDirs);
      assertMissingProperty(includes);
      assertMissingProperty(excludes);
      return;
    }

    assertNotNull(srcDirs.toList());
    assertNotNull(includes.toList());
    assertNotNull(excludes.toList());

    assertSize(entrySuffixes.length, srcDirs.toList());
    assertSize(entrySuffixes.length, includes.toList());
    assertSize(entrySuffixes.length, excludes.toList());

    int i = 0;
    for (int entry : entrySuffixes) {
      assertEquals("srcDirs", name + "Source" + entry, srcDirs.toList().get(i));
      assertEquals("includes", name + "Include" + entry, includes.toList().get(i));
      assertEquals("excludes", name + "Exclude" + entry, excludes.toList().get(i));
      i++;
    }
  }
}
