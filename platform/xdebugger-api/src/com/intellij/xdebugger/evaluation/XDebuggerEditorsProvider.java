package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebuggerEditorsProvider {

  @NotNull
  public abstract FileType getFileType();

  @NotNull
  public abstract Document createDocument(@NotNull Project project, @NotNull String text, @Nullable XSourcePosition sourcePosition);

}
