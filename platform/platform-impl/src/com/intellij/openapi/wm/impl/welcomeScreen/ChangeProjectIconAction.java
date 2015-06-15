/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeProjectIconAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(AnActionEvent e) {
    List<AnAction> elements = getSelectedElements(e);
    String path = ((ReopenProjectAction)elements.get(0)).getProjectPath();
    final ChangeProjectIconForm form = new ChangeProjectIconForm(path);
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
  public void update(AnActionEvent e) {
    boolean enabled = getSelectedElements(e).size() == 1 && !hasGroupSelected(e);
    e.getPresentation().setEnabled(enabled);
  }

  public static class Form {

  }
}
