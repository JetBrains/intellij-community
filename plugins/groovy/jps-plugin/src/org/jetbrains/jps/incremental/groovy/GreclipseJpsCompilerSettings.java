// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull GreclipseJpsCompilerSettings createCopy() {
    return new GreclipseJpsCompilerSettings(mySettings);
  }

  public static @Nullable GreclipseSettings getSettings(@NotNull JpsProject project) {
    GreclipseJpsCompilerSettings extension = project.getContainer().getChild(ROLE);
    return extension != null ? extension.mySettings : null;
  }
}
