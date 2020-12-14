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
package com.android.tools.idea.gradle.dsl.model.android.sourceSets;

import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SourceDirectoryModelImpl extends GradleDslBlockModel implements SourceDirectoryModel {
  @NonNls public static final String EXCLUDES = "mExcludes";
  @NonNls public static final String INCLUDES = "mIncludes";
  @NonNls public static final String SRC_DIRS = "mSrcDirs";

  public SourceDirectoryModelImpl(@NotNull SourceDirectoryDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel excludes() {
    return getModelForProperty(EXCLUDES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel includes() {
    return getModelForProperty(INCLUDES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel srcDirs() {
    return getModelForProperty(SRC_DIRS);
  }
}
