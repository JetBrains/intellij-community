/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IModuleStore extends IComponentStore {

  void setModuleFilePath(final String filePath);

  @Nullable
  VirtualFile getModuleFile();

  @NotNull
  String getModuleFilePath();

  @NotNull
  String getModuleFileName();

  void setOption(final String optionName, final String optionValue);

  void clearOption(final String optionName);

  String getOptionValue(final String optionName);
}
