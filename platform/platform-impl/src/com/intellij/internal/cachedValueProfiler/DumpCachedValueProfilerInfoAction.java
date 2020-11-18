// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class DumpCachedValueProfilerInfoAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    dumpResults(project);
  }

  static void dumpResults(Project project) {
    NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("Cached value profiling");
    try {
      File file = CachedValueProfilerDumper.dumpResults(new File(PathManager.getLogPath()));
      String url = FileUtil.getUrl(file);
      String message = MessageFormat.format("Cached values snapshot is captured to<br>" +
                                            "{0}.<br>" +
                                            "<a href=\"open:{1}\">Open in Editor</a><br/>" +
                                            "<a href=\"{1}\">{2}</a>",
                                            file.getPath(), url, RevealFileAction.getActionName());
      group.createNotification("", message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification,
                                          @NotNull HyperlinkEvent e) {
          if (e.getDescription().startsWith("open:")) {
            VirtualFile virtualFile = project.isDisposed() ? null : LocalFileSystem.getInstance().findFileByPath(
              VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(URLUtil.unescapePercentSequences(e.getDescription().substring(5)))));
            if (virtualFile != null) {
              new OpenFileDescriptor(project, virtualFile).navigate(true);
            }
          }
          else {
            RevealFileAction.FILE_SELECTING_LISTENER.hyperlinkUpdate(notification, e);
          }
        }
      }).notify(project);
    }
    catch (IOException exception) {
      group.createNotification("Failed to capture snapshot: " + exception.getMessage(), NotificationType.ERROR).notify(project);
    }
  }
}