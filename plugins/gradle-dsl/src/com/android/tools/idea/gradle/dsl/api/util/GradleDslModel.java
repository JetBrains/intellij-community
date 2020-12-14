/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.util;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Interface for common functionality for the Dsl models. This interface will be implemented by
 * the model classes as needed.
 */
public interface GradleDslModel {
  /**
   * @return a map containing all of the GradlePropertyModels that are in scope in this model. Note: for block
   * elements this method should include all of the containing properties.
   */
  @NotNull
  Map<String, GradlePropertyModel> getInScopeProperties();

  /**
   * @return a list containing all of the {@link GradlePropertyModel}s that are declared within this element.
   */
  @NotNull
  List<GradlePropertyModel> getDeclaredProperties();

  /**
   * @return the psiElement for this model.
   */
  @Nullable
  PsiElement getPsiElement();
}
