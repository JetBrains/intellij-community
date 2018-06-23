/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes <a href="http://unicode.org/faq/utf_bom.html">file's BOM</a> (if any).
 */
public class RemoveBomAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#" + RemoveBomAction.class);

  public RemoveBomAction() {
    super("Remove BOM");
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null || files.length == 0) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    boolean enabled = false;
    String fromWhere = files[0].getName();
    for (VirtualFile file : files) {
      if (file.isDirectory()) {  // Accurate calculation is very costly especially in presence of excluded directories!
        enabled = true;
        fromWhere = "all files in " + file.getName() + " (recursively)" + (files.length == 1 ? "" : " and others");
        break;
      }
      else if (file.getBOM() != null) {
        enabled = true;
        fromWhere = file.getName() + (files.length == 1 ? "" : " and others");
        break;
      }
    }

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    e.getPresentation().setDescription("Remove byte order mark from "+fromWhere);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null) {
      return;
    }
    List<VirtualFile> filesToProcess = getFilesWithBom(files);
    if (filesToProcess.isEmpty()) return;
    new Task.Backgroundable(getEventProject(e), "Removing BOM", true, () -> false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        for (int i = 0; i < filesToProcess.size(); i++) {
          ProgressManager.checkCanceled();
          VirtualFile virtualFile = filesToProcess.get(i);
          indicator.setFraction(i*1.0/filesToProcess.size());
          indicator.setText2(StringUtil.shortenPathWithEllipsis(virtualFile.getPath(), 40));
          byte[] bom = virtualFile.getBOM();
          if (virtualFile instanceof NewVirtualFile && bom != null) {
            virtualFile.setBOM(null);
            NewVirtualFile file = (NewVirtualFile)virtualFile;
            try {
              byte[] bytes = file.contentsToByteArray();
              byte[] contentWithStrippedBom = new byte[bytes.length - bom.length];
              System.arraycopy(bytes, bom.length, contentWithStrippedBom, 0, contentWithStrippedBom.length);
              WriteAction.runAndWait(() -> file.setBinaryContent(contentWithStrippedBom));
            }
            catch (IOException ex) {
              LOG.warn("Unexpected exception occurred on attempt to remove BOM from file " + file, ex);
            }
          }
        }
      }
    }.queue();
  }

  /**
   * Recursively traverses contents of the given file roots (any root may be directory) and returns files that have
   * {@link VirtualFile#getBOM() BOM} defined.
   *
   * @param roots VFS roots to traverse
   * @return collection of detected files with defined {@link VirtualFile#getBOM() BOM} if any; empty collection otherwise
   */
  @NotNull
  private static List<VirtualFile> getFilesWithBom(@NotNull VirtualFile[] roots) {
    List<VirtualFile> result = new ArrayList<>();
    for (VirtualFile root : roots) {
      getFilesWithBom(root, result);
    }
    return result;
  }

  private static void getFilesWithBom(@NotNull VirtualFile root, @NotNull final List<VirtualFile> result) {
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && file.getBOM() != null) {
          result.add(file);
        }
        return true;
      }
    });
  }
}
