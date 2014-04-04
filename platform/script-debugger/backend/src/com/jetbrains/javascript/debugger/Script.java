package com.jetbrains.javascript.debugger;

import com.jetbrains.javascript.debugger.sourcemap.SourceMap;

/**
 * An objects that holds data for a "script" which is a part of a resource
 * loaded into the browser, identified by its original document URL, line offset
 * in the original document, and the line count this script spans.
 */
public interface Script extends ScriptInfo {
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
}