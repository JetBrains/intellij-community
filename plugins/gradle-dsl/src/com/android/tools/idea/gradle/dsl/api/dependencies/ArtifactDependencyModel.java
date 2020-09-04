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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ArtifactDependencyModel extends DependencyModel {
  @NotNull
  String compactNotation();

  @NotNull
  ResolvedPropertyModel name();

  @NotNull
  ResolvedPropertyModel group();

  @NotNull
  ResolvedPropertyModel version();

  @NotNull
  ResolvedPropertyModel classifier();

  @NotNull
  ResolvedPropertyModel extension();

  /**
   * @return the model representing this entire dependency, this will be either a MAP_TYPE model for map form dependencies. Or
   * a STRING_TYPE model for compact notation.
   * Note: In teh case where this is of STRING_TYPE, the return value of {@link GradlePropertyModel#isModified()} will be shared between
   * all models returned by this class. I.e if you modify the version, this model along will models for name, group, classifier and
   * extension will all be modified. Note: this model will contain the exact string that is in the build file and if parts of it contain
   * references then they will not be resolved.
   */
  @NotNull
  ResolvedPropertyModel completeModel();

  @Nullable
  DependencyConfigurationModel configuration();

  /**
   * Makes any change to this model through name/group/version/classifier/extension work on the result property by first resolving any
   * references. Note: This does not affect completeModel() or compactNotation(), this also has no effect on dependencies declared as maps.
   * These always set through variables.
   */
  void enableSetThrough();

  /**
   * Makes any change to this model through name/group/version/classifier/extension work on the property even if this overwrites variables.
   */
  void disableSetThrough();
}
