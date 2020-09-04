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

import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PackagingOptionsModelImpl extends GradleDslBlockModel implements PackagingOptionsModel {
  // FIXME: implement doNotStrip
  @NonNls public static final String EXCLUDES = "mExcludes";
  @NonNls public static final String MERGES = "mMerges";
  @NonNls public static final String PICK_FIRSTS = "mPickFirsts";

  public PackagingOptionsModelImpl(@NotNull PackagingOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel excludes() {
    return GradlePropertyModelBuilder.create(myDslElement, EXCLUDES).asSet(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel merges() {
    return GradlePropertyModelBuilder.create(myDslElement, MERGES).asSet(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pickFirsts() {
    return GradlePropertyModelBuilder.create(myDslElement, PICK_FIRSTS).asSet(true).buildResolved();
  }
}
