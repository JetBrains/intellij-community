package org.jetbrains.debugger;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.sourcemap.SourceMap;

public interface Script extends UserDataHolderEx, HasUrl {
  void setSourceMap(SourceMap sourceMap);

  enum Type {
    /** A native, internal JavaScript VM script */
    NATIVE,

    /** A script supplied by an extension */
    EXTENSION,

    /** A normal user script */
    NORMAL
  }

  Type getType();

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