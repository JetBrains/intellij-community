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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface DirectoryIndexExcludePolicy {
  ExtensionPointName<DirectoryIndexExcludePolicy> EP_NAME = ExtensionPointName.create("com.intellij.directoryIndexExcludePolicy");

  @NotNull
  VirtualFile[] getExcludeRootsForProject();

  @NotNull
  VirtualFilePointer[] getExcludeRootsForModule(@NotNull ModuleRootModel rootModel);

  @NotNull
  static DirectoryIndexExcludePolicy[] getExtensions(@NotNull AreaInstance areaInstance) {
    return Extensions.getExtensions(EP_NAME, areaInstance);
  }
}
