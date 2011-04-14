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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

/**
 * @author Konstantin Bulenkov
 */
public class TestDirDiffAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final VirtualFile src = VirtualFileManager.getInstance().findFileByUrl(Registry.stringValue("dir.diff.default.src.folder"));
      final VirtualFile trg = VirtualFileManager.getInstance().findFileByUrl(Registry.stringValue("dir.diff.default.trg.folder"));
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
      final VirtualFile[] files1 = src != null ? new VirtualFile[]{src} : FileChooserFactory.getInstance().createFileChooser(descriptor, project).choose(null, project);
      final VirtualFile[] files2 = trg != null ? new VirtualFile[]{trg} : FileChooserFactory.getInstance().createFileChooser(descriptor, project).choose(null, project);
      if (files1.length == 1 && files2.length == 1) {
        DiffElement elem1 = new VirtualFileDiffElement(files1[0]);
        DiffElement elem2 = new VirtualFileDiffElement(files2[0]);
        final DirDiffManager diffManager = DirDiffManager.getInstance(project);
        if (diffManager.canShow(elem1, elem2)) {
          diffManager.showDiff(elem1, elem2, new DirDiffSettings());
        }
      }
    }
  }
}
