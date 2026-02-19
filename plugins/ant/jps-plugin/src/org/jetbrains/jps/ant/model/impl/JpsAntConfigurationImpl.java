// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.ant.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntConfiguration;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class JpsAntConfigurationImpl extends JpsElementBase<JpsAntConfigurationImpl> implements JpsAntConfiguration {
  public static final JpsElementChildRole<JpsAntConfiguration> ROLE = JpsElementChildRoleBase.create("ant configuration");
  private String myProjectDefaultAntName;
  private final Map<String, JpsAntBuildFileOptions> myOptionsMap = new HashMap<>();

  public JpsAntConfigurationImpl(Map<String, JpsAntBuildFileOptions> options, String projectDefaultAntName) {
    myProjectDefaultAntName = projectDefaultAntName;
    myOptionsMap.putAll(options);
  }

  @Override
  public @NotNull JpsAntConfigurationImpl createCopy() {
    return new JpsAntConfigurationImpl(myOptionsMap, myProjectDefaultAntName);
  }

  @Override
  public void setProjectDefaultAntName(@Nullable String projectDefaultAntName) {
    myProjectDefaultAntName = projectDefaultAntName;
  }

  @Override
  public @Nullable String getProjectDefaultAntName() {
    return myProjectDefaultAntName;
  }

  @Override
  public @NotNull Collection<JpsAntBuildFileOptions> getOptionsForAllBuildFiles() {
    return myOptionsMap.values();
  }

  @Override
  public @NotNull JpsAntBuildFileOptions getOptions(@NotNull String buildFileUrl) {
    JpsAntBuildFileOptions options = myOptionsMap.get(buildFileUrl);
    if (options != null) {
      return options;
    }
    return new JpsAntBuildFileOptionsImpl();
  }
}
