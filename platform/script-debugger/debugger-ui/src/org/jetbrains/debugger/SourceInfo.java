package org.jetbrains.debugger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceInfo implements XSourcePosition {
  private final String functionName;

  private final VirtualFile file;
  private final int line;
  private final int column;

  private int offset = -1;

  private SourceInfo(@Nullable String functionName, @NotNull VirtualFile file, int line, int column) {
    this.functionName = functionName;
    this.file = file;
    this.line = line;
    this.column = column;
  }

  @Nullable
  public static SourceInfo create(@Nullable String functionName, @Nullable VirtualFile file, int line, int column) {
    if (file == null || !file.isValid()) {
      return null;
    }
    return new SourceInfo(functionName, file, line, column);
  }

  @Nullable
  public String getFunctionName() {
    return functionName;
  }

  @Override
  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  @Override
  public int getOffset() {
    if (offset == -1) {
      Document document;
      AccessToken token = ReadAction.start();
      try {
        document = file.isValid() ? FileDocumentManager.getInstance().getDocument(file) : null;
      }
      finally {
        token.finish();
      }

      if (document == null) {
        return -1;
      }

      offset = line < document.getLineCount() ? document.getLineStartOffset(line) : -1;
    }
    return offset;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return file;
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project) {
    return new OpenFileDescriptor(project, getFile(), getLine(), getColumn());
  }

  @Override
  public String toString() {
    return getFile() + ":" + getLine() + (column == -1 ? "": (":" + getColumn()));
  }
}
