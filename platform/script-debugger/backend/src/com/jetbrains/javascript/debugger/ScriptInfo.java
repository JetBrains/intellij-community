package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.Url;
import com.jetbrains.javascript.debugger.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ScriptInfo extends UserDataHolderEx {
  @Nullable
  SourceMap getSourceMap();

  @NotNull
  Url getUrl();

  @Nullable
  String getFunctionName();

  int getLine();

  int getColumn();

  int getEndLine();
}