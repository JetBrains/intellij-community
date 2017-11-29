/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for elements of the project model (modules, libraries, artifacts, etc).
 *
 * @author nik
 */
@ApiStatus.Experimental
public interface ProjectModelElement {
  /**
   * Return non-null value if this element was automatically created by the IDE during importing from some external project system rather
   * than created by user manually via UI. Configuration files corresponding to such elements will be stored separately so they won't
   * pollute project directories. Also IDEs will show a warning if user tries to modify settings of such element because these changes may
   * be lost after reimporting from the external model.
   */
  @ApiStatus.Experimental
  @Nullable
  ProjectModelExternalSource getExternalSource();
}
