// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
