package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IModuleStore extends IComponentStore {
  boolean isSavePathsRelative();

  void setModuleFilePath(final String filePath);

  @Nullable
  VirtualFile getModuleFile();

  @NotNull
  String getModuleFilePath();

  @NotNull
  String getModuleFileName();

  void setSavePathsRelative(final boolean b);

  void setOption(final String optionName, final String optionValue);

  void clearOption(final String optionName);

  String getOptionValue(final String optionName);
}
