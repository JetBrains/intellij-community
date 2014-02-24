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
package com.intellij.remotesdk;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteSdkProperties {
  String getInterpreterPath();

  void setInterpreterPath(String interpreterPath);

  String getHelpersPath();

  void setHelpersPath(String helpersPath);

  String getDefaultHelpersName();

  void addRemoteRoot(String remoteRoot);

  void clearRemoteRoots();

  List<String> getRemoteRoots();

  void setRemoteRoots(List<String> remoteRoots);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);

  String getFullInterpreterPath();

  void setSdkId(String sdkId);

  @Deprecated
  boolean isInitialized();

  @Deprecated
  void setInitialized(boolean initialized);
}
