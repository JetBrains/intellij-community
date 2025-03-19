// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public final class EditKeymapsDialog extends SingleConfigurableEditor {
  private final String myActionToSelect;

  public EditKeymapsDialog(Project project, String actionToSelect, boolean showOnlyConflicts) {
    super(project, new KeymapPanel(showOnlyConflicts));
    myActionToSelect = actionToSelect;
  }

  public EditKeymapsDialog(Project project, String actionToSelect) { this(project, actionToSelect, false); }


  @Override
  public void show() {
    if (myActionToSelect != null) {
      SwingUtilities.invokeLater(() -> ((KeymapPanel)getConfigurable()).selectAction(myActionToSelect));
    }
    super.show();
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog";
  }
}
