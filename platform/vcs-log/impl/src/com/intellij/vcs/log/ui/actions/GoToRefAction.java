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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsRef;

import java.util.Collection;
import java.util.concurrent.Future;

public class GoToRefAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    final VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      return;
    }

    Collection<String> refs = ContainerUtil.map(log.getAllReferences(), new Function<VcsRef, String>() {
      @Override
      public String fun(VcsRef ref) {
        return ref.getName();
      }
    });
    FindPopupWithProgress popup = new FindPopupWithProgress(project, refs, new Function<String, Future>() {
            @Override
            public Future fun(String text) {
              return log.jumpToReference(text);
            }
          });
    popup.showUnderneathOf(log.getToolbar());
  }

  @Override
  public void update(AnActionEvent e) {
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && log != null);
  }

}
