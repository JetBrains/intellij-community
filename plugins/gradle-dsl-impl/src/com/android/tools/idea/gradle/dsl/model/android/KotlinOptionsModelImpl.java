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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.KotlinOptionsModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.KotlinOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class KotlinOptionsModelImpl extends GradleDslBlockModel implements KotlinOptionsModel {
  @NonNls public static final String JVM_TARGET = "mJvmTarget";

  public KotlinOptionsModelImpl(@NotNull KotlinOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public LanguageLevelPropertyModel jvmTarget() {
    return getJvmTargetModelForProperty(JVM_TARGET);
  }
}
