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

import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AaptOptionsModelImpl extends GradleDslBlockModel implements AaptOptionsModel {
  @NonNls public static final String ADDITIONAL_PARAMETERS = "mAdditionalParameters";
  @NonNls public static final String CRUNCHER_ENABLED = "mCruncherEnabled";
  @NonNls public static final String CRUNCHER_PROCESSES = "mCruncherProcesses";
  @NonNls public static final String FAIL_ON_MISSING_CONFIG_ENTRY = "mFailOnMissingConfigEntry";
  @NonNls public static final String IGNORE_ASSETS = "mIgnoreAssetsPattern";
  @NonNls public static final String NO_COMPRESS = "mNoCompress";
  @NonNls public static final String NAMESPACED = "mNamespaced";

  public AaptOptionsModelImpl(@NotNull AaptOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel additionalParameters() {
    return GradlePropertyModelBuilder.create(myDslElement, ADDITIONAL_PARAMETERS).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel ignoreAssets() {
    return GradlePropertyModelBuilder.create(myDslElement, IGNORE_ASSETS).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel failOnMissingConfigEntry() {
    return GradlePropertyModelBuilder.create(myDslElement, FAIL_ON_MISSING_CONFIG_ENTRY).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel cruncherProcesses() {
    return GradlePropertyModelBuilder.create(myDslElement, CRUNCHER_PROCESSES).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel cruncherEnabled() {
    return GradlePropertyModelBuilder.create(myDslElement, CRUNCHER_ENABLED).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel noCompress() {
    return GradlePropertyModelBuilder.create(myDslElement, NO_COMPRESS).buildResolved();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel namespaced() {
    return GradlePropertyModelBuilder.create(myDslElement, NAMESPACED).buildResolved();
  }
}
