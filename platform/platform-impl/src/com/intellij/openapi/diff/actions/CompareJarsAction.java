/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Konstantin Bulenkov
 */
public class CompareJarsAction extends AnAction {
  //
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("jar.diff");
    if (project != null && files != null) {
      VirtualFileDiffElement src = null;
      VirtualFileDiffElement trg = null;
      if (files.length == 2 && isJar(files[0]) && isJar(files[1])) {
        src = new JarFileDiffElement(files[0]);
        trg = new JarFileDiffElement(files[1]);
      } else if (files.length == 1 && isJar(files[0])) {
        src = new VirtualFileDiffElement(files[0]);
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false) {
          @Override
          public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
            return file.isDirectory()
                   || (!file.isDirectory() && JarFileSystem.PROTOCOL.equalsIgnoreCase(file.getExtension()));
          }
        };
        final VirtualFile[] result = FileChooserFactory.getInstance()
          .createFileChooser(descriptor, project).choose(project.getBaseDir(), project);
        if (result.length == 1 && result[0] != null && isJar(result[0])) {
          trg = new JarFileDiffElement(result[0]);
        }
      }
      final DirDiffManager mgr = DirDiffManager.getInstance(project);
      if (src != null && trg != null && mgr.canShow(src, trg)) {
        mgr.showDiff(src, trg, new DirDiffSettings());
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 0 && files.length < 3) {
      if (isJar(files[0]) && (files.length == 1 || isJar(files[1]))) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setVisible(true);
        e.getPresentation().setText(files.length == 1 ? "Compare Jar File with..." : "Compare Jar Files");
        return;
      }
    }

    e.getPresentation().setEnabled(false);
    e.getPresentation().setVisible(false);
  }

  private static boolean isJar(VirtualFile file) {
    return JarFileSystem.PROTOCOL.equalsIgnoreCase(file.getExtension());
  }
}
