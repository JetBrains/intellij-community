// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RemoteSdkProperties extends RemoteSdkPropertiesPaths {
  void setInterpreterPath(String interpreterPath);

  void setHelpersPath(String helpersPath);

  String getDefaultHelpersName();

  @NotNull PathMappingSettings getPathMappings();

  void setPathMappings(@Nullable PathMappingSettings pathMappings);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);

  void setSdkId(String sdkId);

  String getSdkId();

  boolean isValid();

  void setValid(boolean valid);

  boolean isRunAsRootViaSudo();

  void setRunAsRootViaSudo(boolean runAsRootViaSudo);
}
