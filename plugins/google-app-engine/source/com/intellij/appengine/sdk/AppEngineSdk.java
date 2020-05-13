/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.sdk;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface AppEngineSdk {

  @NotNull
  String getSdkHomePath();

  @NotNull
  File getAppCfgFile();

  @NotNull
  File getToolsApiJarFile();

  File @NotNull [] getLibraries();

  boolean isClassInWhiteList(@NotNull String className);

  @Nullable
  String getVersion();

  boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName);

  boolean isValid();

  @NotNull
  String getOrmLibDirectoryPath();

  @NotNull
  List<String> getUserLibraryPaths();

  VirtualFile @NotNull [] getOrmLibSources();

  @NotNull
  File getApplicationSchemeFile();

  @NotNull
  File getWebSchemeFile();

  File @NotNull [] getJspLibraries();

  void patchJavaParametersForDevServer(@NotNull ParametersList vmParameters);
}
