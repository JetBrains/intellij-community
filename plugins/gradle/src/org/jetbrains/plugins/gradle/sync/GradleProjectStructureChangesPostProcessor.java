/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;

import java.util.Collection;

/**
 * Defines contract for entity which is eligible to adjust project structure changes.
 * E.g. resolve changes (auto-import), transform them (build one 'dependency library is outdated from 'gradle-local library' and
 * 'intellij-local library' changes etc.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/16/13 5:24 PM
 */
public interface GradleProjectStructureChangesPostProcessor {

  /**
   * Callback to provide current post processor with information about current changes and ability to adjust them in-place.
   * <p/>
   * <b>Note:</b> this callback is assumed to be called from EDT, in order to allow synchronous processing such as
   * given changes collection's modification is immediately visible to the calling code.
   * 
   * @param changes                      current changes which might be adjusted
   * @param project                      current intellij project
   * @param onIdeProjectStructureChange  a flag which identifies if current update is triggered by ide project structure
   *                                     change (an alternative is a manual project structure changes refresh implied by a user)
   */
  void processChanges(@NotNull Collection<GradleProjectStructureChange> changes,
                      @NotNull Project project,
                      boolean onIdeProjectStructureChange);
}
