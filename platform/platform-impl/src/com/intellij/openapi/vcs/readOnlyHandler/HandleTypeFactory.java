package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface HandleTypeFactory {
  ExtensionPointName<HandleTypeFactory> EP_NAME = ExtensionPointName.create("com.intellij.handleTypeFactory"); 

  @Nullable
  HandleType createHandleType(VirtualFile file);
}