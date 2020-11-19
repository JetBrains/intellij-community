// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

final class ApproveRemovedMappingsActivity implements StartupActivity {
  ApproveRemovedMappingsActivity() {
    if (ApplicationManager.getApplication().isUnitTestMode() || !Registry.is("ide.restore.removed.mappings")) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    RemovedMappingTracker removedMappings = ((FileTypeManagerImpl)FileTypeManager.getInstance()).getRemovedMappingTracker();
    List<RemovedMappingTracker.RemovedMapping> unapprovedMappings = removedMappings.deleteUnapprovedMappings();
    if (unapprovedMappings.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      for (RemovedMappingTracker.RemovedMapping mapping : unapprovedMappings) {
        FileNameMatcher matcher = mapping.getFileNameMatcher();
        FileType fileType = FileTypeManager.getInstance().findFileTypeByName(mapping.getFileTypeName());
        if (fileType == null) continue;
        String fileTypeName = fileType.getName();
        String oldFileTypeName = ObjectUtils.notNull(((FileTypeManagerImpl)FileTypeManager.getInstance()).getExtensionMap().findAssociatedFileType(matcher), PlainTextFileType.INSTANCE).getName();
        Notification notification = new Notification(NotificationGroup.createIdWithTitle("File type recognized", FileTypesBundle.message("notification.title.file.type.recognized")),
                                                     FileTypesBundle.message("notification.title.file.type.recognized"),
                                                     FileTypesBundle.message("notification.file.extension.0.was.reassigned.to.1.revert", matcher.getPresentableString(), fileTypeName, oldFileTypeName),
                                                     NotificationType.WARNING, new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
            ApplicationManager.getApplication().runWriteAction(() -> {
              FileType oldFileType = ObjectUtils.notNull(FileTypeManager.getInstance().findFileTypeByName(oldFileTypeName), PlainTextFileType.INSTANCE);
              FileTypeManager.getInstance().associate(oldFileType, matcher);
              removedMappings.add(matcher, fileTypeName, true);
            });
            notification.expire();
          }
        });
        Notifications.Bus.notify(notification, project);
        ApplicationManager.getApplication().runWriteAction(() -> FileTypeManager.getInstance().associate(fileType, matcher));
      }
    });
  }
}
