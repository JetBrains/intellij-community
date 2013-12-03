/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

/**
 * Incorrect VCS root definition: either an unregistered, or an incorrectly registered VCS root.
 *
 * @author Kirill Likhodedov
 * @see VcsRootChecker
 */
public interface VcsRootError {

  enum Type {
    EXTRA_MAPPING,
    UNREGISTERED_ROOT
  }

  /**
   * @return type of mapping error
   */
  @NotNull
  Type getType();

  /**
   * @return affected directory mapping string
   */
  @NotNull
  String getMapping();

  /**
   * @return vcsKey for affected mapping
   */
  @NotNull
  VcsKey getVcsKey();
}
