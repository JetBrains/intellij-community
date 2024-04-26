// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class InlineWatch {
  private final @NotNull XExpression myExpression;
  private final @NotNull VirtualFile myFile;
  private @NotNull XSourcePosition myPosition;
  private @Nullable RangeMarker myRangeMarker;

  public InlineWatch(@NotNull XExpression expression, @NotNull XSourcePosition position) {
    myExpression = expression;
    myFile = position.getFile();
    myPosition = position;
  }

  public @NotNull XExpression getExpression() {
    return myExpression;
  }

  public @NotNull XSourcePosition getPosition() {
      return myPosition;
  }

  public boolean isValid() {
    return myRangeMarker != null && myRangeMarker.isValid();
  }

  public int getLine() {
    return myPosition.getLine();
  }

  public void updatePosition() {
    if (myRangeMarker != null && myRangeMarker.isValid()) {
      int line = myRangeMarker.getDocument().getLineNumber(myRangeMarker.getStartOffset());
      if (line != myPosition.getLine()) {
        myPosition = Objects.requireNonNull(XDebuggerUtil.getInstance().createPosition(myFile, line));
      }
    }
  }

  /**
   * @return true if marker was added successfully
   */
  public boolean setMarker() {
    Document document = FileDocumentManager.getInstance().getDocument(myFile);
    if (document != null) {
      int line = myPosition.getLine();
      if (DocumentUtil.isValidLine(line, document)) {
        int offset = document.getLineEndOffset(line);
        myRangeMarker = document.createRangeMarker(offset, offset, true);
        return true;
      }
    }
    return false;
  }
}
