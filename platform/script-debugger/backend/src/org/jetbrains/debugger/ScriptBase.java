package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.sourcemap.SourceMap;

public abstract class ScriptBase extends UserDataHolderBase implements Script {
  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<String> source;

  private final Url url;
  protected final int line;
  protected final int column;
  protected final int endLine;

  protected final Type type;

  private SourceMap sourceMap;

  protected ScriptBase(Type type, @NotNull Url url, int line, int column, int endLine) {
    this.type = type;
    this.url = url;
    this.line = line;
    this.column = column;
    this.endLine = endLine;
  }

  @NotNull
  @Override
  public Url getUrl() {
    return url;
  }

  @Nullable
  @Override
  public String getFunctionName() {
    return null;
  }

  @Override
  @Nullable
  public SourceMap getSourceMap() {
    return sourceMap;
  }

  @Override
  public void setSourceMap(@Nullable SourceMap sourceMap) {
    this.sourceMap = sourceMap;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public int getLine() {
    return line;
  }

  @Override
  public int getColumn() {
    return column;
  }

  @Override
  public int getEndLine() {
    return endLine;
  }

  @Override
  public String toString() {
    return "[url=" + getUrl() + ", lineRange=[" + getLine() + ';' + getEndLine() + "]]";
  }
}