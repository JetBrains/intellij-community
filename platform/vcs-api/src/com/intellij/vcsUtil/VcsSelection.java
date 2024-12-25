// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

public class VcsSelection {
  private final Document myDocument;
  private final @NotNull TextRange myTextRange;
  private final @ActionText String myActionName;
  private final @DialogTitle String myDialogTitle;

  public VcsSelection(@NotNull Document document, @NotNull TextRange textRange, String actionName) {
    myDocument = document;
    myTextRange = textRange;
    myActionName = VcsBundle.message("show.history.action.name.template", actionName);
    myDialogTitle = VcsBundle.message("show.history.dialog.title.template", actionName);
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  public int getSelectionStartLineNumber() {
    return safeGetDocumentLine(myTextRange.getStartOffset());
  }

  public int getSelectionEndLineNumber() {
    return safeGetDocumentLine(myTextRange.getEndOffset());
  }

  private int safeGetDocumentLine(int offset) {
    if (offset >= myDocument.getTextLength()) {
      return myDocument.getLineCount() - 1;
    }
    return myDocument.getLineNumber(offset);
  }

  public @ActionText String getActionName() {
    return myActionName;
  }

  public @DialogTitle String getDialogTitle() {
    return myDialogTitle;
  }
}