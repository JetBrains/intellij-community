/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsRef;

import java.util.Collection;

public class GoToRefAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    final VcsLog log = e.getData(VcsLogDataKeys.VSC_LOG);
    if (project == null || log == null) {
      return;
    }

    Collection<String> refs = ContainerUtil.map(log.getAllReferences(), new Function<VcsRef, String>() {
      @Override
      public String fun(VcsRef ref) {
        return ref.getName();
      }
    });
    final PopupWithTextFieldWithAutoCompletion textField = new PopupWithTextFieldWithAutoCompletion(project, refs);
    JBPopup popup = textField.createPopup();
    popup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if (event.isOk()) {
          log.jumpToReference(textField.getText());
        }
      }
    });
    popup.showUnderneathOf(log.getToolbar());
  }

  @Override
  public void update(AnActionEvent e) {
    VcsLog log = e.getData(VcsLogDataKeys.VSC_LOG);
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && log != null);
  }

}
