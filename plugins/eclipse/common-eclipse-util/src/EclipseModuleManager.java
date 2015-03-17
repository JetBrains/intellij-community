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
package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface EclipseModuleManager {
  void setInvalidJdk(String invalidJdk);

  @Nullable
  String getInvalidJdk();

  void registerCon(String name);

  String[] getUsedCons();

  void registerEclipseVariablePath(String path, String var);

  void registerEclipseSrcVariablePath(String path, String var);

  void registerEclipseLinkedSrcVarPath(String path, String var);

  @Nullable
  String getEclipseLinkedSrcVariablePath(String path);

  void registerEclipseLinkedVarPath(String path, String var);

  @Nullable
  String getEclipseLinkedVarPath(String path);

  @Nullable
  String getEclipseVariablePath(String path);

  @Nullable
  String getEclipseSrcVariablePath(String path);

  void registerUnknownCons(String con);

  @NotNull
  Set<String> getUnknownCons();

  boolean isForceConfigureJDK();

  void setForceConfigureJDK();

  void registerEclipseLibUrl(String url);

  boolean isEclipseLibUrl(String url);

  void setExpectedModuleSourcePlace(int expectedModuleSourcePlace);

  boolean isExpectedModuleSourcePlace(int expectedPlace);

  void registerSrcPlace(String srcUrl, int placeIdx);

  int getSrcPlace(String srcUtl);
}
