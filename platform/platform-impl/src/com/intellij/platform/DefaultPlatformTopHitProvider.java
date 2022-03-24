// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.ide.ActionsTopHitProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class DefaultPlatformTopHitProvider extends ActionsTopHitProvider {
  private static final String[][] ACTION_MATRIX = {
     {"op", "open ", "OpenFile"},
     {"reo", "reopen ", "$LRU"},
     {"new c", "new class ", "NewClass"},
     {"new i", "new interface ", "NewClass"},
     {"new e", "new enum ", "NewClass"},
     {"line", "line numbers ", "EditorToggleShowLineNumbers"},
     {"show li", "show line numbers ", "EditorToggleShowLineNumbers"},
     {"gutt", "gutter icons ", "EditorToggleShowGutterIcons"},
     {"show gu", "show gutter icons ", "EditorToggleShowGutterIcons"},
     {"ann", "annotate ", "Annotate"},
     {"wrap", "wraps ", "EditorToggleUseSoftWraps"},
     {"soft w", "soft wraps ", "EditorToggleUseSoftWraps"},
     {"use sof", "use soft wraps ", "EditorToggleUseSoftWraps"},
     {"use wr", "use wraps ", "EditorToggleUseSoftWraps"},
     {"ref", "refactor ", "Refactorings.QuickListPopupAction"},
     {"mov", "move ", "Move"},
     {"ren", "rename  ", "RenameElement"},
   };

  @Override
  protected String[] @NotNull [] getActionsMatrix() {
    return ACTION_MATRIX;
  }
}
