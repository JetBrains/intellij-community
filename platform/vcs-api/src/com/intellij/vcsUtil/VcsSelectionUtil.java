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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.IndexNotReadyException;
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
    for(VcsSelectionProvider provider: Extensions.getExtensions(VcsSelectionProvider.EP_NAME)) {
      try {
        final VcsSelection vcsSelection = provider.getSelection(context);
        if (vcsSelection != null) return vcsSelection;
      }
      catch (IndexNotReadyException ignored) {
      }
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