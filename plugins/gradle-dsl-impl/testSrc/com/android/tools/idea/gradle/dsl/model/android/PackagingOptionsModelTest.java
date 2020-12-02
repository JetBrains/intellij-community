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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_APPEND_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_REMOVE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_REMOVE_ONLY_ELEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_REPLACE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.PACKAGING_OPTIONS_MODEL_REPLACE_ELEMENTS_EXPECTED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link PackagingOptionsModel}.
 */
public class PackagingOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsInApplicationStatements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testParseElementsInAssignmentStatements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testReplaceElements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_REPLACE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.excludes().getListValue("exclude1").setValue("excludeX");
    packagingOptions.merges().getListValue("merge2").setValue("mergeX");
    packagingOptions.pickFirsts().getListValue("pickFirst3").setValue("pickFirstX");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, PACKAGING_OPTIONS_MODEL_REPLACE_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("excludeX", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "mergeX", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirstX"), packagingOptions.pickFirsts());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertMissingProperty("merges", packagingOptions.merges());
    assertMissingProperty("pickFirsts", packagingOptions.pickFirsts());

    packagingOptions.excludes().addListValue().setValue("exclude");
    packagingOptions.merges().addListValue().setValue("merge1");
    packagingOptions.merges().addListValue().setValue("merge2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst1");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst3");

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    if(!isGroovy()) {
      verifyFileContents(myBuildFile, PACKAGING_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED);
    }
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testAppendElements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_APPEND_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1"), packagingOptions.pickFirsts());

    packagingOptions.excludes().addListValue().setValue("exclude2");
    packagingOptions.merges().addListValue().setValue("merge2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst2");

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, PACKAGING_OPTIONS_MODEL_APPEND_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.excludes().delete();
    packagingOptions.merges().delete();
    packagingOptions.pickFirsts().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertMissingProperty("merges", packagingOptions.merges());
    assertMissingProperty("pickFirsts", packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveOneOfElements() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.excludes().getListValue("exclude1").delete();
    packagingOptions.merges().getListValue("merge2").delete();
    packagingOptions.pickFirsts().getListValue("pickFirst3").delete();

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, PACKAGING_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveOnlyElement() throws Exception {
    writeToBuildFile(PACKAGING_OPTIONS_MODEL_REMOVE_ONLY_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst"), packagingOptions.pickFirsts());

    packagingOptions.excludes().getListValue("exclude").delete();
    packagingOptions.merges().getListValue("merge").delete();
    packagingOptions.pickFirsts().getListValue("pickFirst").delete();

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, PACKAGING_OPTIONS_MODEL_REMOVE_ONLY_ELEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertEmpty("merges", packagingOptions.merges().toList());
    assertEmpty("pickFirsts", packagingOptions.pickFirsts().toList());
  }
}
