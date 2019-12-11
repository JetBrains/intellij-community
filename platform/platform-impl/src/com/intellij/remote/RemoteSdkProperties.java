/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
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
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2015")
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
