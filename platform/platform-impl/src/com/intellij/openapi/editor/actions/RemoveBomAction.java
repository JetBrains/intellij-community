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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows to remove <a href="http://unicode.org/faq/utf_bom.html">file's BOM</a> (if any).
 * 
 * @author Denis Zhdanov
 * @since 3/30/11 11:14 AM
 */
public class RemoveBomAction extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#" + RemoveBomAction.class);
  
  public RemoveBomAction() {
    super("Remove BOM");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null) {
      return;
    }
    List<VirtualFile> filesToProcess = getFilesWithBom(files, true);
    for (VirtualFile virtualFile : filesToProcess) {
      byte[] bom = virtualFile.getBOM();
      assert bom != null;
      if (virtualFile instanceof NewVirtualFile) {
        virtualFile.setBOM(null);
        NewVirtualFile file = (NewVirtualFile)virtualFile;
        try {
          byte[] bytes = file.contentsToByteArray();
          byte[] contentWithStrippedBom = new byte[bytes.length - bom.length];
          System.arraycopy(bytes, bom.length, contentWithStrippedBom, 0, contentWithStrippedBom.length);
          file.setBinaryContent(contentWithStrippedBom);
        }
        catch (IOException ex) {
          LOG.warn("Unexpected exception occurred on attempt to remove BOM from file " + file, ex);
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    boolean enabled = false;
    for(VirtualFile file:files) {
      if (file.isDirectory()) {  // Accurate calculation is very costly especially in presence of excluded directories!
        enabled = true;
        break;
      } else if (file.getBOM() != null) {
        enabled = true;
        break;
      }
    }

    e.getPresentation().setEnabled(enabled);
  }

  /**
   * Recursively traverses contents of the given file roots (any root may be directory) and returns files that have
   * {@link VirtualFile#getBOM() BOM} defined.
   * 
   * @param roots   VFS roots to traverse
   * @param all     flag the defines if all files with {@link VirtualFile#getBOM() BOM} should be collected or just any of them
   * @return        collection of detected files with defined {@link VirtualFile#getBOM() BOM} if any; empty collection otherwise
   */
  @NotNull
  private static List<VirtualFile> getFilesWithBom(@NotNull VirtualFile[] roots, boolean all) {
    List<VirtualFile> result = new ArrayList<>();
    for (VirtualFile root : roots) {
      if (!all && !result.isEmpty()) {
        break;
      }
      getFilesWithBom(root, result, all);
    }
    return result;
  }
  
  private static void getFilesWithBom(@NotNull VirtualFile root, @NotNull final List<VirtualFile> result, final boolean all) {
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          if (!all && !result.isEmpty()) {
            return false;
          }
        }
        else if (file.getBOM() != null) {
          result.add(file);
        }
        return true;
      }
    });
  }
}
