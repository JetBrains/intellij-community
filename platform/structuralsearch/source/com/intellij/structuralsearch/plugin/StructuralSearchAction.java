package com.intellij.structuralsearch.plugin;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.SearchDialog;

public class StructuralSearchAction extends AnAction {

  /** Handles IDEA action event
   * @param event the event of action
   */
  public void actionPerformed(AnActionEvent event) {
    triggerAction(null, SearchContext.buildFromDataContext(event.getDataContext()));
  }

  public static void triggerAction(Configuration config, SearchContext searchContext) {
    UsageTrigger.trigger("structural.search");
    final Project project = searchContext.getProject();
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    //StructuralSearchPlugin.getInstance(searchContext.getProject());
    final SearchDialog searchDialog = new SearchDialog(searchContext);

    if (config!=null) {
      searchDialog.setUseLastConfiguration(true);
      searchDialog.setValuesFromConfig(config);
    }

    searchDialog.show();
  }

  /** Updates the state of the action
   * @param event the action event
   */
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext context = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    final StructuralSearchPlugin plugin = project==null ? null:StructuralSearchPlugin.getInstance( project );

    if (plugin == null || plugin.isSearchInProgress() || plugin.isDialogVisible()) {
      presentation.setEnabled( false );
    } else {
      presentation.setEnabled( true );
    }

    super.update(event);
  }

}

