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

import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

public interface SourceSetModel extends GradleDslModel {
  @NotNull
  String name();

  /**
   * Renames the source set, this only changes the name where the model is first defined it does not
   * attempt to update any references to the model.
   *
   * @param newName the new name
   */
  void rename(@NotNull String newName);

  @NotNull
  ResolvedPropertyModel root();

  @NotNull
  SourceDirectoryModel aidl();

  void removeAidl();

  @NotNull
  SourceDirectoryModel assets();

  void removeAssets();

  @NotNull
  SourceDirectoryModel java();

  void removeJava();

  @NotNull
  SourceDirectoryModel jni();

  void removeJni();

  @NotNull
  SourceDirectoryModel jniLibs();

  void removeJniLibs();

  @NotNull
  SourceFileModel manifest();

  void removeManifest();

  @NotNull
  SourceDirectoryModel renderscript();

  void removeRenderscript();

  @NotNull
  SourceDirectoryModel res();

  void removeRes();

  @NotNull
  SourceDirectoryModel resources();

  void removeResources();
}
