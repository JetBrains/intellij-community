/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public Document getDocument() {
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

  @ActionText
  public String getActionName() {
    return myActionName;
  }

  @DialogTitle
  public String getDialogTitle() {
    return myDialogTitle;
  }
}