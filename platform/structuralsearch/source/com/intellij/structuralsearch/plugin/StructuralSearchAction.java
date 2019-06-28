// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.SearchDialog;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StructuralSearchAction extends AnAction {

  /** Handles IDEA action event
   * @param event the event of action
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    triggerAction(null, new SearchContext(event.getDataContext()), false);
  }

  public static void triggerAction(Configuration config, SearchContext searchContext, boolean replace) {
    final Project project = searchContext.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final DialogWrapper dialog = StructuralSearchPlugin.getInstance(project).getDialog();
    if (dialog != null) {
      final JComponent component = dialog.getPreferredFocusedComponent();
      assert component != null;
      IdeFocusManager.getInstance(project).requestFocus(component, true);
      return;
    }

    if (Registry.is("ssr.use.new.search.dialog")) {
      final StructuralSearchDialog searchDialog = new StructuralSearchDialog(searchContext, replace);
      if (config != null) {
        searchDialog.setUseLastConfiguration(true);
        searchDialog.loadConfiguration(config);
      }
      searchDialog.show();
    }
    else {
      final SearchDialog searchDialog = replace ? new ReplaceDialog(searchContext) : new SearchDialog(searchContext);
      if (config != null) {
        searchDialog.setUseLastConfiguration(true);
        searchDialog.setValuesFromConfig(config);
      }
      searchDialog.show();
    }
  }

  /** Updates the state of the action
   * @param event the action event
   */
  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getProject();
    final StructuralSearchPlugin plugin = (project == null) ? null : StructuralSearchPlugin.getInstance(project);

    if (plugin == null || plugin.isSearchInProgress()) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(true);
    }
  }

}
