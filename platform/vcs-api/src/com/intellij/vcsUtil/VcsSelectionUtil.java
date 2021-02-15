// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.IndexNotReadyException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class VcsSelectionUtil {
  private VcsSelectionUtil() {
  }

  @Nullable
  public static VcsSelection getSelection(@NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      for (VcsSelectionProvider provider : VcsSelectionProvider.EP_NAME.getExtensionList()) {
        try {
          final VcsSelection vcsSelection = provider.getSelection(context);
          if (vcsSelection != null) return vcsSelection;
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    }

    return new VcsSelection(editor.getDocument(), selectionModel);
  }
}