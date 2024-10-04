// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public final class VcsSelectionUtil {
  private VcsSelectionUtil() {
  }

  public static @Nullable VcsSelection getSelection(@NotNull AnAction action, @NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    VcsSelection atCaret = e.getUpdateSession()
      .compute(action, "getSelection", ActionUpdateThread.EDT, () -> {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection() && !EditorUtil.contextMenuInvokedOutsideOfSelection(e)) {
          return new VcsSelection(editor.getDocument(),
                                  new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()),
                                  VcsBundle.message("action.name.show.history.for.selection"));
        }
        return null;
      });
    if (atCaret != null) return atCaret;

    VcsSelection vcsSelection = getSelectionFromExtensions(e.getDataContext());
    if (vcsSelection != null) return vcsSelection;

    Caret caret = editor.getCaretModel().getPrimaryCaret();
    return new VcsSelection(editor.getDocument(),
                            new TextRange(caret.getOffset(), caret.getOffset()),
                            VcsBundle.message("action.name.show.history.for.selection"));
  }

  private static @Nullable VcsSelection getSelectionFromExtensions(@NotNull DataContext dataContext) {
    for (VcsSelectionProvider provider : VcsSelectionProvider.EP_NAME.getExtensionList()) {
      try {
        final VcsSelection vcsSelection = provider.getSelection(dataContext);
        if (vcsSelection != null) return vcsSelection;
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    return null;
  }
}