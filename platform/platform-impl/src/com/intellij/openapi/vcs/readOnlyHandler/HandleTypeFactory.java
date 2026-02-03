// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public interface HandleTypeFactory {
  ExtensionPointName<HandleTypeFactory> EP_NAME = ExtensionPointName.create("com.intellij.handleTypeFactory"); 

  @Nullable
  HandleType createHandleType(VirtualFile file);
}