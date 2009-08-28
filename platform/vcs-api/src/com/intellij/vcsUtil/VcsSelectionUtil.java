package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vcs.actions.VcsContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsSelectionUtil {
  private VcsSelectionUtil() {
  }

  @Nullable
  public static VcsSelection getSelection(VcsContext context) {

    VcsSelection selectionFromEditor = getSelectionFromEditor(context);
    if (selectionFromEditor != null) {
      return selectionFromEditor;
    }
    final VcsSelectionProvider[] providers = Extensions.getExtensions(VcsSelectionProvider.EP_NAME);
    for(VcsSelectionProvider provider: providers) {
      final VcsSelection vcsSelection = provider.getSelection(context);
      if (vcsSelection != null) return vcsSelection;
    }
    return null;
  }

  @Nullable
  private static VcsSelection getSelectionFromEditor(VcsContext context) {
    Editor editor = context.getEditor();
    if (editor == null) return null;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return null;
    }
    return new VcsSelection(editor.getDocument(), selectionModel);
  }
}