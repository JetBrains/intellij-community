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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.List;

public class VcsLogCopyHashAction extends DumbAwareAction {

  public VcsLogCopyHashAction() {
    super("Copy Hash", "Copy commit hash value to the clipboard", AllIcons.Actions.Copy);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsLog log = getLog(e);
    if (log == null) {
      return;
    }
    List<Hash> commits = log.getSelectedCommits();
    if (commits.isEmpty()) {
      return;
    }

    String hashes = StringUtil.join(commits, new Function<Hash, String>() {
      @Override
      public String fun(Hash hash) {
        return hash.asString();
      }
    }, "\n");
    CopyPasteManager.getInstance().setContents(new StringSelection(hashes));
  }

  @Override
  public void update(AnActionEvent e) {
    VcsLog log = getLog(e);
    if (log == null) {
      getTemplatePresentation().setVisible(false);
      getTemplatePresentation().setEnabled(false);
    }
    else if (log.getSelectedCommits().isEmpty()) {
      getTemplatePresentation().setVisible(true);
      getTemplatePresentation().setEnabled(false);
    }
    else {
      getTemplatePresentation().setVisible(true);
      getTemplatePresentation().setEnabled(true);
    }
  }

  @Nullable
  private static VcsLog getLog(AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) {
      return null;
    }
    return ServiceManager.getService(project, VcsLog.class);
  }

}
