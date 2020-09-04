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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DependenciesModel {
  @NotNull
  List<DependencyModel> all();

  @NotNull
  List<ArtifactDependencyModel> artifacts(@NotNull String configurationName);

  @NotNull
  List<ArtifactDependencyModel> artifacts();

  void addArtifact(@NotNull String configurationName, @NotNull String compactNotation);

  boolean containsArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency);

  void addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency);

  void addArtifact(@NotNull String configurationName,
                   @NotNull ArtifactDependencySpec dependency,
                   @NotNull List<ArtifactDependencySpec> excludes);

  /**
   * Replaces the artifact dependency which contains the given {@link PsiElement} with a new dependency given by
   * the {@link ArtifactDependencySpec}. If no dependency that contains the {@link PsiElement} exists nothing is
   * changed. Returns {@code true} is a dependency was successfully replaced, {@code false} otherwise.
   */
  boolean replaceArtifactByPsiElement(@NotNull PsiElement oldPsiElement, ArtifactDependencySpec newArtifact);

  @NotNull
  List<ModuleDependencyModel> modules();

  void addModule(@NotNull String configurationName, @NotNull String path);

  void addModule(@NotNull String configurationName, @NotNull String path, @Nullable String config);

  @NotNull
  List<FileTreeDependencyModel> fileTrees();

  void addFileTree(@NotNull String configurationName, @NotNull String dir);

  void addFileTree(@NotNull String configurationName,
                   @NotNull String dir,
                   @Nullable List<String> includes,
                   @Nullable List<String> excludes);


  @NotNull
  List<FileDependencyModel> files();

  void addFile(@NotNull String configurationName, @NotNull String file);

  void remove(@NotNull DependencyModel dependency);
}
