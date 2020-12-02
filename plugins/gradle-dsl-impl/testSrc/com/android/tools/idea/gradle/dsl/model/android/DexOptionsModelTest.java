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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_EDIT_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_REMOVE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEX_OPTIONS_MODEL_REMOVE_ONLY_ELEMENT_IN_THE_LIST;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.DexOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link DexOptionsModel}.
 */
public class DexOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsInApplicationStatements() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());
  }

  @Test
  public void testParseElementsInAssignmentStatements() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("ijkl", "mnop"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "1024m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.FALSE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.TRUE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(5), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.FALSE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.TRUE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(10), dexOptions.threadCount());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_EDIT_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());

    dexOptions.additionalParameters().getListValue("efgh").setValue("xyz");
    dexOptions.javaMaxHeapSize().setValue("1024m");
    dexOptions.jumboMode().setValue(false);
    dexOptions.keepRuntimeAnnotatedClasses().setValue(true);
    dexOptions.maxProcessCount().setValue(5);
    dexOptions.optimize().setValue(false);
    dexOptions.preDexLibraries().setValue(true);
    dexOptions.threadCount().setValue(10);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEX_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "xyz"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "1024m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.FALSE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.TRUE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(5), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.FALSE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.TRUE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(10), dexOptions.threadCount());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
    assertMissingProperty("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertMissingProperty("jumboMode", dexOptions.jumboMode());
    assertMissingProperty("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertMissingProperty("maxProcessCount", dexOptions.maxProcessCount());
    assertMissingProperty("optimize", dexOptions.optimize());
    assertMissingProperty("preDexLibraries", dexOptions.preDexLibraries());
    assertMissingProperty("threadCount", dexOptions.threadCount());

    dexOptions.additionalParameters().addListValue().setValue("abcd");
    dexOptions.javaMaxHeapSize().setValue("2048m");
    dexOptions.jumboMode().setValue(true);
    dexOptions.keepRuntimeAnnotatedClasses().setValue(false);
    dexOptions.maxProcessCount().setValue(10);
    dexOptions.optimize().setValue(true);
    dexOptions.preDexLibraries().setValue(false);
    dexOptions.threadCount().setValue(5);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEX_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    checkForValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());

    dexOptions.additionalParameters().delete();
    dexOptions.javaMaxHeapSize().delete();
    dexOptions.jumboMode().delete();
    dexOptions.keepRuntimeAnnotatedClasses().delete();
    dexOptions.maxProcessCount().delete();
    dexOptions.optimize().delete();
    dexOptions.preDexLibraries().delete();
    dexOptions.threadCount().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
    assertMissingProperty("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertMissingProperty("jumboMode", dexOptions.jumboMode());
    assertMissingProperty("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertMissingProperty("maxProcessCount", dexOptions.maxProcessCount());
    assertMissingProperty("optimize", dexOptions.optimize());
    assertMissingProperty("preDexLibraries", dexOptions.preDexLibraries());
    assertMissingProperty("threadCount", dexOptions.threadCount());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());

    dexOptions.additionalParameters().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, DEX_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), dexOptions.additionalParameters());
  }

  @Test
  public void testRemoveOnlyElementInTheList() throws Exception {
    writeToBuildFile(DEX_OPTIONS_MODEL_REMOVE_ONLY_ELEMENT_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    checkForValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd"), dexOptions.additionalParameters());

    dexOptions.additionalParameters().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
  }
}
