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

import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AdbOptionsModelImpl extends GradleDslBlockModel implements AdbOptionsModel {
  @NonNls public static final String INSTALL_OPTIONS = "mInstallOptions";
  @NonNls public static final String TIME_OUT_IN_MS = "mTimeOutInMs";

  public AdbOptionsModelImpl(@NotNull AdbOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel installOptions() {
    return GradlePropertyModelBuilder.create(myDslElement, INSTALL_OPTIONS).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel timeOutInMs() {
    return GradlePropertyModelBuilder.create(myDslElement, TIME_OUT_IN_MS).buildResolved();
  }
}
