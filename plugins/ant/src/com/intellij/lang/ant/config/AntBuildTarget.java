/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface AntBuildTarget {
  @Nullable
  String getName();

  @Nullable
  String getDisplayName();

  /**
   * @return target names as defined in the underlying ant build file in the order they should be executed
   *         For normal targets this is a singleton list with the target name that getName() method returns. For meta-targets this is
   *         a list of targets that form the meta-target
   */
  @NotNull
  default List<String> getTargetNames() {
    final String name = getName();
    return name == null? Collections.emptyList() : Collections.singletonList(name);
  }

  @Nullable
  String getNotEmptyDescription();

  boolean isDefault();

  void run(DataContext dataContext, List<BuildFileProperty> additionalProperties, AntBuildListener buildListener);

  AntBuildModel getModel();
}
