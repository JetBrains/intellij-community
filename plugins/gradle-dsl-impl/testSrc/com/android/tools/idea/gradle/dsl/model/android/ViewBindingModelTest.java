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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_ADD_ELEMENTS_FROM_EXISTING;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_ADD_ELEMENTS_FROM_EXISTING_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_EDIT_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_PARSE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.VIEW_BINDING_MODEL_REMOVE_ELEMENTS;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ViewBindingModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

/**
 * Tests for {@link ViewBindingModel}.
 */
public class ViewBindingModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(VIEW_BINDING_MODEL_PARSE_ELEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ViewBindingModel viewBinding = android.viewBinding();
    assertEquals("enabled", Boolean.FALSE, viewBinding.enabled());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(VIEW_BINDING_MODEL_EDIT_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ViewBindingModel viewBinding = android.viewBinding();
    assertEquals("enabled", Boolean.FALSE, viewBinding.enabled());

    viewBinding.enabled().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, VIEW_BINDING_MODEL_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    viewBinding = android.viewBinding();
    assertEquals("enabled", Boolean.TRUE, viewBinding.enabled());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(VIEW_BINDING_MODEL_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ViewBindingModel viewBinding = android.viewBinding();
    assertMissingProperty("enabled", viewBinding.enabled());

    viewBinding.enabled().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, VIEW_BINDING_MODEL_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    viewBinding = android.viewBinding();
    assertEquals("enabled", Boolean.FALSE, viewBinding.enabled());
  }

  @Test
  public void testAddElementsFromExisting() throws Exception {
    writeToBuildFile(VIEW_BINDING_MODEL_ADD_ELEMENTS_FROM_EXISTING);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ViewBindingModel viewBinding = android.viewBinding();
    assertMissingProperty("enabled", viewBinding.enabled());

    viewBinding.enabled().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, VIEW_BINDING_MODEL_ADD_ELEMENTS_FROM_EXISTING_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    viewBinding = android.viewBinding();
    assertEquals("enabled", Boolean.FALSE, viewBinding.enabled());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(VIEW_BINDING_MODEL_REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ViewBindingModel viewBinding = android.viewBinding();
    checkForValidPsiElement(viewBinding, ViewBindingModelImpl.class);
    assertEquals("enabled", Boolean.FALSE, viewBinding.enabled());

    viewBinding.enabled().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    viewBinding = android.viewBinding();
    checkForInValidPsiElement(viewBinding, ViewBindingModelImpl.class);
    assertMissingProperty("enabled", viewBinding.enabled());
  }
}
