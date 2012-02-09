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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 11/17/11 12:50 PM
 */
public class GradleModulePresenceChange extends GradleEntityPresenceChange<String> {

  public GradleModulePresenceChange(@Nullable String gradleModuleName, @Nullable String intellijModuleName)
    throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.entity.type.module"), gradleModuleName, intellijModuleName);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}
