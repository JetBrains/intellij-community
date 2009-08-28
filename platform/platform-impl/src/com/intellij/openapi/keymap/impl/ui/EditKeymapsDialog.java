package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class EditKeymapsDialog extends SingleConfigurableEditor {
  private final String myActionToSelect;

  public EditKeymapsDialog(Project project, String actionToSelect) {
    super(project, new KeymapPanel());
    myActionToSelect = actionToSelect;
  }

  public void show() {
    if (myActionToSelect != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ((KeymapPanel)getConfigurable()).selectAction(myActionToSelect);
        }
      });
    }
    super.show();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog";
  }
}
