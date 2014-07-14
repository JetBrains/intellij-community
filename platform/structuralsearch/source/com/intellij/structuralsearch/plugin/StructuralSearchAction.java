package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.SearchDialog;

public class StructuralSearchAction extends AnAction {

  public StructuralSearchAction() {
    super(SSRBundle.message("structuralsearch.action"));
  }

  /** Handles IDEA action event
   * @param event the event of action
   */
  public void actionPerformed(AnActionEvent event) {
    triggerAction(null, SearchContext.buildFromDataContext(event.getDataContext()));
  }

  public static void triggerAction(Configuration config, SearchContext searchContext) {
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

