// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

class ConflictingMappingTracker {
  private static final Logger LOG = Logger.getInstance(ConflictingMappingTracker.class);
  private static boolean throwOnConflict;

  @TestOnly
  static void throwOnConflict(boolean doThrow) {
    throwOnConflict = doThrow;
  }

  /**
   * there is a conflict: a matcher belongs to several file types, so
   * {@code matcher} removed from {@code fileTypeNameOld} and assigned to {@code fileTypeNameNew}
   */
  void addConflict(@Nullable Project project,
                   @NotNull FileNameMatcher matcher,
                   @NotNull String fileTypeNameOld,
                   @NotNull String fileTypeNameNew) {
    String title = FileTypesBundle.message("notification.title.file.type.conflict.found", fileTypeNameOld, fileTypeNameNew);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AssertionError error = new AssertionError(title + "; matcher: " + matcher);
      if (throwOnConflict) {
        throw error;
      }
      else {
        LOG.warn(error);
      }
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      FileType fileTypeOld = FileTypeManager.getInstance().findFileTypeByName(fileTypeNameOld);
      FileType fileTypeNew = FileTypeManager.getInstance().findFileTypeByName(fileTypeNameNew);

      String message = FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), fileTypeNameNew);
      Notification notification = new Notification(
        NotificationGroup.createIdWithTitle("File type conflict", FileTypesBundle.message("notification.title.file.type.conflict")),
        title,
        message,
        NotificationType.WARNING, null);
      notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.revert.to", fileTypeNameOld), () -> {
        if (fileTypeOld != null) {
          ApplicationManager.getApplication().runWriteAction(() -> FileTypeManager.getInstance().associate(fileTypeOld, matcher));
          notification.expire();
          String m = FileTypesBundle.message("dialog.message.file.pattern.was.reassigned.back.to", matcher.getPresentableString(), fileTypeNameOld);
          Messages.showMessageDialog(project, m, FileTypesBundle.message("dialog.title.pattern.reassigned"), Messages.getInformationIcon());
        }
      }));
      if (fileTypeOld != null) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", fileTypeNameOld), () -> editFileType(project, fileTypeOld)));
      }
      if (fileTypeNew != null) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", fileTypeNameNew), () -> editFileType(project, fileTypeNew)));
      }
      Notifications.Bus.notify(notification, project);
    }, project == null ? ApplicationManager.getApplication().getDisposed() : project.getDisposed());
  }

  private static void editFileType(@Nullable Project project, @NotNull FileType fileType) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project,
         configurable -> configurable instanceof SearchableConfigurable && ((SearchableConfigurable)configurable).getId().equals("preferences.fileTypes"),
         configurable -> {
           if (configurable instanceof ConfigurableWrapper) {
             configurable = (Configurable)((ConfigurableWrapper)configurable).getConfigurable();
           }
           FileTypeSelectable fileTypeSelectable = (FileTypeSelectable)configurable;
           fileTypeSelectable.selectFileType(fileType);
         });
  }
}
