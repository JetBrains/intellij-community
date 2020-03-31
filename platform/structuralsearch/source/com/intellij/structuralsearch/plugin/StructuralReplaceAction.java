// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import org.jetbrains.annotations.NotNull;

/**
 * Search and replace structural java code patterns action.
 */
public class StructuralReplaceAction extends StructuralSearchAction {

  /** Handles IDEA action event
   * @param event the event of action
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    triggerAction(null, new SearchContext(event.getDataContext()), true);
  }
}

