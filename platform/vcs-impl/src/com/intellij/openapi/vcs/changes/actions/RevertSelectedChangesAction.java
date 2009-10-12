/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.Convertor;

import javax.swing.*;

public class RevertSelectedChangesAction extends RevertCommittedStuffAbstractAction {
  private static Icon ourIcon;
  private static String ourText;

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    initPresentation();
    presentation.setIcon(ourIcon);
    presentation.setText(ourText);
    super.update(e);
  }

  private static void initPresentation() {
    if (ourIcon == null) {
      ourIcon = IconLoader.getIcon("/actions/rollback.png");
      ourText = VcsBundle.message("action.revert.selected.changes.text");
    }
  }

  public RevertSelectedChangesAction() {
    super(new Convertor<AnActionEvent, Change[]>() {
      public Change[] convert(AnActionEvent e) {
        return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
      }
    }, new Convertor<AnActionEvent, Change[]>() {
      public Change[] convert(AnActionEvent e) {
        // to ensure directory flags for SVN are initialized
        e.getData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
        return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
      }
    });
  }
}
