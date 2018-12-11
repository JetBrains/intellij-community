// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeProjectIconAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<AnAction> elements = getSelectedElements(e);
    final ChangeProjectIconForm form = new ChangeProjectIconForm(((ReopenProjectAction)elements.get(0)).getProjectPath());
    DialogWrapper dialog = new DialogWrapper(null) {
      {
        init();
      }

      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        return form.myRootPanel;
      }
    };
    dialog.show();
    if (dialog.isOK()) {
      try {
        form.apply();
      }
      catch (IOException e1) {
        System.out.println(e1);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = getSelectedElements(e).size() == 1 && !hasGroupSelected(e);
    e.getPresentation().setEnabled(enabled);
  }

  public static class Form {

  }
}
