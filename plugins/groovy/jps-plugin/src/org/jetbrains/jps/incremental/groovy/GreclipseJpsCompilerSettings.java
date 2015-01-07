/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

public class GreclipseJpsCompilerSettings extends JpsCompositeElementBase<GreclipseJpsCompilerSettings> {
  public static final JpsElementChildRole<GreclipseJpsCompilerSettings> ROLE = JpsElementChildRoleBase.create("Greclipse Compiler Configuration");

  private GreclipseSettings mySettings;

  public GreclipseJpsCompilerSettings(@NotNull GreclipseSettings settings) {
    mySettings = settings;
  }

  @NotNull
  @Override
  public GreclipseJpsCompilerSettings createCopy() {
    return new GreclipseJpsCompilerSettings(mySettings);
  }

  @Override
  public void applyChanges(@NotNull GreclipseJpsCompilerSettings modified) {
    mySettings = modified.mySettings;
  }

  @Nullable
  public static GreclipseSettings getSettings(@NotNull JpsProject project) {
    GreclipseJpsCompilerSettings extension = project.getContainer().getChild(ROLE);
    return extension != null ? extension.mySettings : null;
  }
}
