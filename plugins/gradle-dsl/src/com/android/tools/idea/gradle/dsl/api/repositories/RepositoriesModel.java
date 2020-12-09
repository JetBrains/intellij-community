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
package com.android.tools.idea.gradle.dsl.api.repositories;

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RepositoriesModel extends GradleDslModel {
  @NotNull
  List<RepositoryModel> repositories();

  void addRepositoryByMethodName(@NotNull String methodName);

  void addFlatDirRepository(@NotNull String dirName);

  boolean containsMethodCall(@NotNull String methodName);

  void addMavenRepositoryByUrl(@NotNull String url, @Nullable String name);

  boolean containsMavenRepositoryByUrl(@NotNull String repositoryUrl);

  boolean removeRepositoryByUrl(@NotNull String repositoryUrl);

  boolean hasGoogleMavenRepository();

}
