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

import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CompileOptionsModelImpl extends BaseCompileOptionsModelImpl implements
                                                                         CompileOptionsModel {
  @NonNls public static final String ENCODING = "mEncoding";
  @NonNls public static final String INCREMENTAL = "mIncremental";

  public CompileOptionsModelImpl(@NotNull BaseCompileOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel encoding() {
    return getModelForProperty(ENCODING);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel incremental() {
    return getModelForProperty(INCREMENTAL);
  }
}
