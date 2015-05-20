/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.diff.JarFileDiffElement;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated
public class CompareJarsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("jar.diff");
    if (project != null && files != null) {
      VirtualFileDiffElement src = null;
      VirtualFileDiffElement trg = null;
      if (files.length == 2 && isArchive(files[0]) && isArchive(files[1])) {
        src = new JarFileDiffElement(files[0]);
        trg = new JarFileDiffElement(files[1]);
      }
      else if (files.length == 1 && isArchive(files[0])) {
        src = new JarFileDiffElement(files[0]);
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false) {
          @Override
          public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
            return file.isDirectory()
                   || (!file.isDirectory() && isArchive(file));
          }
        };
        final VirtualFile[] result = FileChooser.chooseFiles(descriptor, project, project.getBaseDir());
        if (result.length == 1 && result[0] != null && isArchive(result[0])) {
          trg = new JarFileDiffElement(result[0]);
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
      if (isArchive(files[0]) && (files.length == 1 || isArchive(files[1]))) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setVisible(true);
        e.getPresentation().setText(files.length == 1 ? "Compare Archive File with..." : "Compare Archives");
        return;
      }
    }

    e.getPresentation().setEnabled(false);
    e.getPresentation().setVisible(false);
  }

  private static boolean isArchive(VirtualFile file) {
    return file.getFileType() instanceof ArchiveFileType;
  }
}
