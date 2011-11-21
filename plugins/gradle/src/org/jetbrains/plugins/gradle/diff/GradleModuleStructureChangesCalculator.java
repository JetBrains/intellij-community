/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleModule;

import java.util.Collection;
import java.util.Set;

/**
 * //TODO den add doc
 *
 * @author Denis Zhdanov
 * @since 11/14/11 6:30 PM
 */
public class GradleModuleStructureChangesCalculator {

  //TODO den add doc
  @NotNull
  public Collection<GradleProjectStructureChange> calculateDiff(@NotNull GradleModule gradleModule,
                                                                @NotNull Module intellijModule,
                                                                @NotNull Set<GradleProjectStructureChange> knownChanges)
  {
    //TODO den implement
    return new HashSet<GradleProjectStructureChange>();
  }
}
