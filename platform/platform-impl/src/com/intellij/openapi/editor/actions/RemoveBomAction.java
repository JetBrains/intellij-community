// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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
import com.intellij.openapi.vfs.CharsetToolkit;
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
  private static final Logger LOG = Logger.getInstance(RemoveBomAction.class);

  public RemoveBomAction() {
    super(IdeBundle.messagePointer("remove.byte.order.mark"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
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
    String finalFromWhere = fromWhere;
    e.getPresentation().setDescription(IdeBundle.messagePointer("remove.byte.order.mark.from", finalFromWhere));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) {
      return;
    }
    List<VirtualFile> filesToProcess = getFilesWithBom(files);
    if (filesToProcess.isEmpty()) return;
    List<VirtualFile> filesUnableToProcess = new ArrayList<>();
    new Task.Backgroundable(getEventProject(e), IdeBundle.message("removing.byte.order.mark"), true, () -> false) {
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
            if (isBOMMandatory(virtualFile) ) {
              filesUnableToProcess.add(virtualFile);
            }
            else {
              doRemoveBOM(virtualFile, bom);
            }
          }
        }

        if (!filesUnableToProcess.isEmpty()) {
          String title = IdeBundle.message("notification.title.was.unable.to.remove.bom.in", filesUnableToProcess.size(),
                                           StringUtil.pluralize("file", filesUnableToProcess.size()));
          String msg = IdeBundle.message("notification.content.mandatory.bom.br", filesUnableToProcess.size(),
                                         StringUtil.join(filesUnableToProcess, VirtualFile::getName, "<br/>    "));
          Notifications.Bus.notify(new Notification(
            NotificationGroup.createIdWithTitle("Failed to remove BOM", IdeBundle.message("notification.group.failed.to.remove.bom")),
            title, msg, NotificationType.ERROR));
        }
      }
    }.queue();
  }

  private static boolean isBOMMandatory(@NotNull VirtualFile file) {
    return CharsetToolkit.getMandatoryBom(file.getCharset()) != null;
  }

  private static void doRemoveBOM(@NotNull VirtualFile virtualFile, byte @NotNull [] bom) {
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

  /**
   * Recursively traverses contents of the given file roots (any root may be directory) and returns files that have
   * {@link VirtualFile#getBOM() BOM} defined.
   *
   * @param roots VFS roots to traverse
   * @return collection of detected files with defined {@link VirtualFile#getBOM() BOM} if any; empty collection otherwise
   */
  @NotNull
  private static List<VirtualFile> getFilesWithBom(VirtualFile @NotNull [] roots) {
    List<VirtualFile> result = new ArrayList<>();
    for (VirtualFile root : roots) {
      getFilesWithBom(root, result);
    }
    return result;
  }

  private static void getFilesWithBom(@NotNull VirtualFile root, @NotNull final List<? super VirtualFile> result) {
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
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
