// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.*;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface EditorModel extends Disposable {
  DocumentEx getDocument();
  MarkupModelEx getEditorMarkupModel();
  MarkupModelEx getDocumentMarkupModel();
  EditorHighlighter getHighlighter();
  InlayModelEx getInlayModel();
  FoldingModelEx getFoldingModel();
  SoftWrapModelEx getSoftWrapModel();
  CaretModel getCaretModel();
  SelectionModel getSelectionModel();
  ScrollingModel getScrollingModel();
  FocusModeModel getFocusModel();
  boolean isAd();
}
