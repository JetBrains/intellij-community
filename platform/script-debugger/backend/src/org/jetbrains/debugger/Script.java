package org.jetbrains.debugger;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.sourcemap.SourceMap;

/**
 * An objects that holds data for a "script" which is a part of a resource
 * loaded into the browser, identified by its original document URL, line offset
 * in the original document, and the line count this script spans.
 */
public interface Script extends UserDataHolderEx {
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