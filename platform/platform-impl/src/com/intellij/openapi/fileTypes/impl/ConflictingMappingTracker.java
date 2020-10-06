// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
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
import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

class ConflictingMappingTracker {
  private static final Logger LOG = Logger.getInstance(ConflictingMappingTracker.class);

  /**
   * Somebody tries to assign matcher (which was previously assigned to the oldFileType) to the newFileType.
   * If there is a conflict, show notification "some plugin is going to override file type".
   * Assign matcher to the corresponding file type according to the following heuristics:
   * - if plugin tries to override bundled file type, assign matcher to the newFileType. (We assume plugin knows what it's doing)
   * - OTOH, if bundled file type tries to override file type from a plugin, leave assignment matcher-to-oldFileType. (Same reason)
   * - otherwise, take a leap of faith and assign matcher to the newFileType. (and show notification)
   * @return file type assigned to matcher and the notification text explaining what's going on
   */
  @NotNull
  FileType warnAndResolveConflict(@NotNull FileNameMatcher matcher,
                                  @Nullable FileType oldFileType,
                                  @NotNull FileType newFileType) {
    if (oldFileType != null && !oldFileType.equals(newFileType) && !(oldFileType instanceof AbstractFileType)) {
      Pair.NonNull<FileType, String> result = resolveConflict(matcher, oldFileType, newFileType);
      FileType resolved = result.getFirst();
      if (!oldFileType.equals(resolved)) {
        addConflict(null, matcher, oldFileType, resolved, result.getSecond());
      }
      return resolved;
    }
    return ObjectUtils.notNull(oldFileType, newFileType);
  }

  @NotNull
  private static Pair.NonNull<FileType, String> resolveConflict(@NotNull FileNameMatcher matcher,
                                                                @NotNull FileType oldFileType,
                                                                @NotNull FileType newFileType) {
    PluginDescriptor oldPlugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(oldFileType.getClass().getName());
    PluginDescriptor newPlugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(newFileType.getClass().getName());
    if (newPlugin != null && !newPlugin.equals(oldPlugin) && !newPlugin.isBundled() && (oldPlugin == null || oldPlugin.isBundled())) {
      // new plugin overrides core or bundled plugin
      String message =
      FileTypesBundle.message("notification.content.file.pattern.was.reassigned.by.plugin", newPlugin.getName(), matcher.getPresentableString(), newFileType.getDisplayName());
      return Pair.createNonNull(newFileType, message);
    }
    if (oldPlugin != null && !oldPlugin.equals(newPlugin) && !oldPlugin.isBundled() && (newPlugin == null || newPlugin.isBundled())) {
      // old plugin overrides core or bundled plugin
      String message =
        FileTypesBundle.message("notification.content.file.pattern.was.reassigned.by.plugin", oldPlugin.getName(), matcher.getPresentableString(), oldFileType.getDisplayName());
      return Pair.createNonNull(oldFileType, message);
    }
    if (oldPlugin != null && !oldPlugin.isBundled() && newPlugin != null && !newPlugin.isBundled()) {
      // one plugin tries to override the other
      String message =
        FileTypesBundle.message("notification.content.file.pattern.from.plugin.was.reassigned.by.another.plugin", matcher.getPresentableString(), oldPlugin.getName(), newFileType.getDisplayName(), newPlugin.getName());
      return Pair.createNonNull(newFileType, message);
    }
    // ? wild guess
    String message =
      FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), newFileType.getDisplayName());

    return Pair.createNonNull(newFileType, message);
  }

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
  private void addConflict(@Nullable Project project,
                           @NotNull FileNameMatcher matcher,
                           @NotNull FileType oldFileType,
                           @NotNull FileType newFileType, @NotNull String message) {
    if (oldFileType.equals(newFileType)) {
      throw new IllegalArgumentException("expected different file types but got "+newFileType);
    }
    String oldName = oldFileType.getDisplayName();
    String newName = newFileType.getDisplayName();
    String title = FileTypesBundle.message("notification.title.file.type.conflict.found", oldName, newName);
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
      Notification notification = new Notification(
        NotificationGroup.createIdWithTitle("File type conflict", FileTypesBundle.message("notification.title.file.type.conflict")),
        title,
        message,
        NotificationType.WARNING, null);
      notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.conflict.confirm",
                                                                                     newName), () -> {
        // mark as removed from fileTypeOld and associated with fileTypeNew
        ApplicationManager.getApplication().runWriteAction(() -> {
          myRemovedMappingTracker.add(matcher, oldName, true);
          FileTypeManager.getInstance().associate(newFileType, matcher);
        });
        notification.expire();
        String m = FileTypesBundle.message("dialog.message.file.pattern.was.assigned.to", matcher.getPresentableString(), newName);
        Messages.showMessageDialog(project, m, FileTypesBundle.message("dialog.title.pattern.reassigned"), Messages.getInformationIcon());
      }));
      notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.revert.to",
                                                                                     oldName), () -> {
        // mark as removed from fileTypeNew and associated with fileTypeOld
        ApplicationManager.getApplication().runWriteAction(() -> {
          myRemovedMappingTracker.add(matcher, newName, true);
          FileTypeManager.getInstance().associate(oldFileType, matcher);
        });
        notification.expire();
        String m = FileTypesBundle.message("dialog.message.file.pattern.was.reassigned.back.to", matcher.getPresentableString(), oldName);
        Messages.showMessageDialog(project, m, FileTypesBundle.message("dialog.title.pattern.reassigned"), Messages.getInformationIcon());
      }));
      if (!oldFileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit",
                                                                                       oldName), () -> editFileType(project, oldFileType)));
      }
      if (!newFileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit",
                                                                                       newName), () -> editFileType(project, newFileType)));
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
