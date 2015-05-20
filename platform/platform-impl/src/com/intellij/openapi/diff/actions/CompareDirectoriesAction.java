/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated
public class CompareDirectoriesAction extends DumbAwareAction {

  public static final String LAST_USED_KEY = "dir.diff.last.used.directory";

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("dir.diff");
    if (project != null && files != null) {
      VirtualFileDiffElement src = null;
      VirtualFileDiffElement trg = null;
      if (files.length == 2 && files[0].isDirectory() && files[1].isDirectory()) {
        src = new VirtualFileDiffElement(files[0]);
        trg = new VirtualFileDiffElement(files[1]);
      }
      else if (files.length == 1 && files[0].isDirectory()) {
        src = new VirtualFileDiffElement(files[0]);
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        final String path = PropertiesComponent.getInstance(project).getValue(LAST_USED_KEY);
        VirtualFile toSelect = null;
        if (path != null) {
          toSelect = LocalFileSystem.getInstance().findFileByPath(path);
        }
        if (toSelect == null) {
          toSelect = project.getBaseDir();
        }
        final VirtualFile[] result = FileChooser.chooseFiles(descriptor, project, toSelect);
        if (result.length == 1 && result[0] != null && result[0].isDirectory()) {
          PropertiesComponent.getInstance(project).setValue(LAST_USED_KEY, result[0].getPath());
          trg = new VirtualFileDiffElement(result[0]);
        }
      }
      final DirDiffManager mgr = DirDiffManager.getInstance(project);
      if (src != null && trg != null && mgr.canShow(src, trg)) {
        mgr.showDiff(src, trg, new DirDiffSettings(), null);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 0 && files.length < 3) {
      if (files[0].isDirectory() && (files.length == 1 || files[1].isDirectory())) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setVisible(true);
        e.getPresentation().setText(files.length == 1 ? "Compare Directory with..." : "Compare Directories");
        return;
      }
    }

    e.getPresentation().setEnabled(false);
    e.getPresentation().setVisible(false);
  }
}
