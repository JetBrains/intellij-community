// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageTarget extends NavigationItem {
  UsageTarget[] EMPTY_ARRAY = new UsageTarget[0];

  boolean isValid();

  /**
   * Should open usage view and look for usages
   */
  void findUsages();

  /**
   * Should look for usages in one specific editor. This typicaly shows other kind of dialog and doesn't
   * result in usage view display.
   */
  default void findUsagesInEditor(@NotNull FileEditor editor) {
    // no op
  }

  default void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {
    // no op
  }

  default boolean isReadOnly() {
    return true;
  }

  /**
   * @return the files this usage target is in. Might be null if usage target is not file-based
   */
  default VirtualFile @Nullable [] getFiles() {
    return null;
  }

  default void update() {
    // no op
  }
}
