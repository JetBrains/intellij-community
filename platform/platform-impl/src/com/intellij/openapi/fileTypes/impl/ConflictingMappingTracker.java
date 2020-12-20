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
  enum ConflictPolicy {
    THROW, LOG_ERROR, IGNORE
  }
  private static ConflictPolicy throwOnConflict = ConflictPolicy.IGNORE;
  private final RemovedMappingTracker myRemovedMappingTracker;

  ConflictingMappingTracker(@NotNull RemovedMappingTracker removedMappingTracker) {
    myRemovedMappingTracker = removedMappingTracker;
  }

  @TestOnly
  static void onConflict(@NotNull ConflictPolicy doThrow) {
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
    if (fileTypeNameNew.equals(fileTypeNameOld)) {
      throw new IllegalArgumentException("expected different file types but got "+fileTypeNameOld);
    }
    String title = FileTypesBundle.message("notification.title.file.type.conflict.found", fileTypeNameOld, fileTypeNameNew);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AssertionError error = new AssertionError(title + "; matcher: " + matcher);
      switch(throwOnConflict) {
        case THROW:
          throw error;
        case LOG_ERROR:
          LOG.error(error);
          break;
        case IGNORE:
          break;
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
      if (fileTypeNew != null) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.conflict.confirm", fileTypeNameNew), () -> {
          if (fileTypeOld != null) {
            // mark as removed from fileTypeOld and associated with fileTypeNew
            ApplicationManager.getApplication().runWriteAction(() -> {
              myRemovedMappingTracker.add(matcher, fileTypeNameOld, true);
              FileTypeManager.getInstance().associate(fileTypeNew, matcher);
            });
            notification.expire();
            String m = FileTypesBundle.message("dialog.message.file.pattern.was.assigned.to", matcher.getPresentableString(), fileTypeNameNew);
            Messages.showMessageDialog(project, m, FileTypesBundle.message("dialog.title.pattern.reassigned"), Messages.getInformationIcon());
          }
        }));
      }
      if (fileTypeOld != null) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.revert.to", fileTypeNameOld), () -> {
          // mark as removed from fileTypeNew and associated with fileTypeOld
          ApplicationManager.getApplication().runWriteAction(() -> {
            myRemovedMappingTracker.add(matcher, fileTypeNameNew, true);
            FileTypeManager.getInstance().associate(fileTypeOld, matcher);
          });
          notification.expire();
          String m = FileTypesBundle.message("dialog.message.file.pattern.was.reassigned.back.to", matcher.getPresentableString(), fileTypeNameOld);
          Messages.showMessageDialog(project, m, FileTypesBundle.message("dialog.title.pattern.reassigned"), Messages.getInformationIcon());
        }));
      }
      if (fileTypeOld != null && !fileTypeOld.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", fileTypeNameOld), () -> editFileType(project, fileTypeOld)));
      }
      if (fileTypeNew != null && !fileTypeNew.isReadOnly()) {
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
