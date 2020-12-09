/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

public interface SigningConfigModel extends GradleDslModel {
  @NotNull
  String name();

  /**
   * Renames this SigningConfigModel, this only changes the name where the model is first defined it does not
   * attempt to update any references to the model.
   *
   * @param newName the new name
   */
  default void rename(@NotNull String newName) {
    rename(newName, false);
  }

  void rename(@NotNull String newName, boolean renameReferences);

  @NotNull
  ResolvedPropertyModel storeFile();

  @NotNull
  PasswordPropertyModel storePassword();

  @NotNull
  ResolvedPropertyModel storeType();

  @NotNull
  ResolvedPropertyModel keyAlias();

  @NotNull
  PasswordPropertyModel keyPassword();
}
