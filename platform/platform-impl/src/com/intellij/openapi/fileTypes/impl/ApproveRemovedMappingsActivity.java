// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ApproveRemovedMappingsActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() || !Registry.is("ide.restore.removed.mappings")) return;

    final Map<FileNameMatcher,Pair<FileType,Boolean>> map = ((FileTypeManagerImpl)FileTypeManager.getInstance()).getRemovedMappings();
    if (!map.isEmpty()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        for (Iterator<Map.Entry<FileNameMatcher, Pair<FileType, Boolean>>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<FileNameMatcher, Pair<FileType, Boolean>> entry = iterator.next();
          if (entry.getValue().getSecond()) {
            continue;
          }
          final FileNameMatcher matcher = entry.getKey();
          final FileType fileType = entry.getValue().getFirst();
          Notification notification = new Notification("File type recognized", "File type recognized",
                                                       "File extension " + matcher.getPresentableString() +
                                                       " was reassigned to " + fileType.getName() + " <a href='revert'>Revert</a>",
                                                       NotificationType.WARNING, new NotificationListener.Adapter() {
            @Override
            protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
              ApplicationManager.getApplication().runWriteAction(() -> {
                FileTypeManager.getInstance().associate(PlainTextFileType.INSTANCE, matcher);
                map.put(matcher, Pair.create(fileType, true));
              });
              notification.expire();
            }
          });
          Notifications.Bus.notify(notification, project);
          ApplicationManager.getApplication().runWriteAction(() -> FileTypeManager.getInstance().associate(fileType, matcher));
          iterator.remove();
        }
      });
    }
  }
}
