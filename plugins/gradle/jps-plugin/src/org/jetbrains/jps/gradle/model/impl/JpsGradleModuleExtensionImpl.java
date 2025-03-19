// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.JpsGradleModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Vladislav.Soroka
 */
public class JpsGradleModuleExtensionImpl extends JpsElementBase<JpsGradleModuleExtensionImpl> implements JpsGradleModuleExtension {
  public static final JpsElementChildRole<JpsGradleModuleExtension> ROLE = JpsElementChildRoleBase.create("gradle");

  private final String myModuleType;

  public JpsGradleModuleExtensionImpl(String moduleType) {
    myModuleType = moduleType;
  }

  @Override
  public @Nullable String getModuleType() {
    return myModuleType;
  }

  @Override
  public @NotNull JpsGradleModuleExtensionImpl createCopy() {
    return new JpsGradleModuleExtensionImpl(myModuleType);
  }
}
