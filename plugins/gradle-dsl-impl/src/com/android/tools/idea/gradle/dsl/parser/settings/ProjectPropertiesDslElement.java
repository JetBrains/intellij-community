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
package com.android.tools.idea.gradle.dsl.parser.settings;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ProjectPropertiesDslElement extends GradlePropertiesDslElement {
  @NonNls public static final String PROJECT_DIR = "projectDir";
  @NonNls public static final String BUILD_FILE_NAME = "buildFileName";

  public ProjectPropertiesDslElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @Nullable
  public File projectDir() {
    GradleDslMethodCall projectDir = getPropertyElement(PROJECT_DIR, GradleDslMethodCall.class);
    if (projectDir != null) {
      return projectDir.getValue(File.class);
    }
    return null;
  }

  @Nullable
  @Override
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    // This element should not be involved in anchoring, skip and request anchor from parent.
    if (myParent instanceof GradlePropertiesDslElement) {
      return myParent.requestAnchor(element);
    }

    return super.requestAnchor(element);
  }

  @Nullable
  public static String getStandardProjectKey(@NotNull String projectReference) {
    String standardForm = projectReference.replaceAll("\\s", "").replace("\"", "'");
    if (standardForm.startsWith("project(':") && standardForm.endsWith("')")) {
      return standardForm;
    }
    return null;
  }
}
