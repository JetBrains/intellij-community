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

/**
 * @author Denis Zhdanov
 * @since 11/16/11 8:49 PM
 */
public class GradleProjectStructureChangeVisitorAdapter implements GradleProjectStructureChangeVisitor {

  @Override
  public void visit(@NotNull GradleRenameChange change) {
  }

  @Override
  public void visit(@NotNull GradleProjectStructureChange change) {
  }

  @Override
  public void visit(@NotNull GradleModulePresenceChange change) {
  }

  @Override
  public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
  }
}
