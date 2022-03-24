// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RemoteSdkProperties extends RemoteSdkPropertiesPaths {

  void setInterpreterPath(String interpreterPath);

  void setHelpersPath(String helpersPath);

  String getDefaultHelpersName();

  @NotNull
  PathMappingSettings getPathMappings();

  void setPathMappings(@Nullable PathMappingSettings pathMappings);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);

  void setSdkId(String sdkId);

  String getSdkId();

  /**
   * isValid() is used now
   * To be removed in IDEA 15
   *
   * @deprecated
   */
  @Deprecated(forRemoval = true)
  boolean isInitialized();

  @Deprecated
  void setInitialized(boolean initialized);

  boolean isValid();

  void setValid(boolean valid);

  /**
   * <b>Note:</b> This method will be abstract.
   */
  default boolean isRunAsRootViaSudo() {
    return false;
  }

  /**
   * <b>Note:</b> This method will be abstract.
   */
  default void setRunAsRootViaSudo(boolean runAsRootViaSudo) {
  }
}
