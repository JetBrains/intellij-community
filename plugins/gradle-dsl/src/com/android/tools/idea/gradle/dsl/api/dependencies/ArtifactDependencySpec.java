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
package com.android.tools.idea.gradle.dsl.api.dependencies;

import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ArtifactDependencySpec {
  @Nullable
  static ArtifactDependencySpec create(@NotNull String notation) {
    return GradleModelProvider.get().getArtifactDependencySpec(notation);
  }

  @NotNull
  static ArtifactDependencySpec create(@NotNull ArtifactDependencyModel dependency) {
    return GradleModelProvider.get().getArtifactDependencySpec(dependency);
  }

  @NotNull
  static ArtifactDependencySpec create(@NotNull String name,
                                       @Nullable String group,
                                       @Nullable String version) {
    return GradleModelProvider.get().getArtifactDependencySpec(name, group, version);
  }

  @NotNull
  static ArtifactDependencySpec create(@NotNull String name,
                                       @Nullable String group,
                                       @Nullable String version,
                                       @Nullable String classifier,
                                       @Nullable String extension) {
    return GradleModelProvider.get().getArtifactDependencySpec(name, group, version, classifier, extension);
  }

  boolean equalsIgnoreVersion(Object o);

  @NotNull
  String getName();

  @Nullable
  String getGroup();

  @Nullable
  String getVersion();

  @Nullable
  String getClassifier();

  @Nullable
  String getExtension();

  void setName(@NotNull String name);

  void setGroup(@Nullable String group);

  void setVersion(@Nullable String version);

  void setClassifier(@Nullable String classifer);

  void setExtension(@Nullable String extension);

  @Override
  boolean equals(Object o);

  @Override
  int hashCode();

  @Override
  String toString();

  @NotNull
  String compactNotation();
}
