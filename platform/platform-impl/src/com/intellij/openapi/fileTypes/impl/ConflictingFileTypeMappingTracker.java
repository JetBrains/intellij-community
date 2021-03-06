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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

class ConflictingFileTypeMappingTracker {
  private static final Logger LOG = Logger.getInstance(ConflictingFileTypeMappingTracker.class);

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
  ResolveConflictResult warnAndResolveConflict(@NotNull FileNameMatcher matcher,
                                               @Nullable FileType oldFileType,
                                               @NotNull FileType newFileType) {
    if (oldFileType == null || oldFileType.equals(newFileType) || oldFileType instanceof AbstractFileType) {
      // no conflict really
      return new ResolveConflictResult(ObjectUtils.notNull(oldFileType, newFileType), null, null, "", "", true);
    }

    ResolveConflictResult result = resolveConflict(matcher, oldFileType, newFileType);
    // notify about only real conflicts between two same-league plugins
    if (!result.resolved.equals(oldFileType) && !result.approved) {
      showConflictNotification(null, matcher, oldFileType, result);
    }
    return result;
  }

  static class ResolveConflictResult {
    final @NotNull FileType resolved;
    final String resolvedPluginName;
    final String oldPluginName;
    final @NotNull @Nls String notification;
    final @NotNull @Nls String explanation;
    final boolean approved;

    private ResolveConflictResult(@NotNull FileType resolved, @Nullable String resolvedPluginName, @Nullable String oldPluginName, @NotNull @Nls String notification, @NotNull @Nls String explanation, boolean approved) {
      this.resolved = resolved;
      this.resolvedPluginName = resolvedPluginName;
      this.oldPluginName = oldPluginName;
      this.notification = notification;
      this.explanation = explanation;
      this.approved = approved;
    }
  }
  @NotNull
  private static ResolveConflictResult resolveConflict(@NotNull FileNameMatcher matcher,
                                                       @NotNull FileType oldFileType,
                                                       @NotNull FileType newFileType) {
    PluginDescriptor oldPlugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(oldFileType.getClass().getName());
    PluginDescriptor newPlugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(newFileType.getClass().getName());

    if (newPlugin == null || newPlugin.isBundled() && oldPlugin != null && !oldPlugin.isBundled()) {
      PluginDescriptor plugin = newPlugin;
      FileType type = newFileType;
      newPlugin = oldPlugin;
      newFileType = oldFileType;
      oldFileType = type;
      oldPlugin = plugin;
    }
    // do not show notification if the new plugin reassigned core or bundled plugin
    String oldPluginName = oldPlugin == null || oldPlugin.isBundled() ? "bundled" : oldPlugin.getName();
    String explanation = FileTypesBundle.message("notification.content.file.type.reassigned.explanation", matcher.getPresentableString());
    if (newPlugin != null) {
      boolean approved = oldPlugin == null || !oldPlugin.equals(newPlugin) && oldPlugin.isBundled() && !newPlugin.isBundled();
      // new plugin overrides pattern
      String message = FileTypesBundle.message("notification.content.file.type.reassigned.plugin", matcher.getPresentableString(), oldPluginName, newFileType.getDisplayName(), newPlugin.getName());
      String newPluginName = newPlugin.isBundled() ? null : newPlugin.getName();
      return new ResolveConflictResult(newFileType, newPluginName, oldPlugin==null||oldPlugin.isBundled() ? null : oldPlugin.getName(), message, explanation, approved);
    }
    /* ? wild guess*/
    String message = FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), oldFileType.getDisplayName());
    // prefer old file type to avoid notification about file type reassignments twice
    return new ResolveConflictResult(oldFileType, null, null, message, explanation, false);
  }

  enum ConflictPolicy {
    THROW, LOG_ERROR, IGNORE
  }
  private static ConflictPolicy throwOnConflict = ConflictPolicy.IGNORE;
  private final RemovedMappingTracker myRemovedMappingTracker;

  ConflictingFileTypeMappingTracker(@NotNull RemovedMappingTracker removedMappingTracker) {
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
  private void showConflictNotification(@Nullable Project project,
                                        @NotNull FileNameMatcher matcher,
                                        @NotNull FileType oldFileType,
                                        @NotNull ResolveConflictResult result) {
    FileType resolvedFileType = result.resolved;
    @Nls String notificationText = result.notification;
    if (oldFileType.equals(resolvedFileType)) {
      throw new IllegalArgumentException("expected different file types but got "+resolvedFileType);
    }
    String oldDisplayName = oldFileType.getDisplayName();
    String resolvedDisplayName = resolvedFileType.getDisplayName();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AssertionError error = new AssertionError(notificationText + "; matcher: " + matcher);
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
        notificationText,
        result.explanation,
        NotificationType.INFORMATION, null);
      // if approved==true, there's no need to explicitly confirm
      if (!result.approved) {
        String message = result.resolvedPluginName == null ? FileTypesBundle.message("notification.content.conflict.confirm.reassign", resolvedDisplayName) :
                         FileTypesBundle.message("notification.content.conflict.confirm.reassign.from.plugin", resolvedDisplayName, result.resolvedPluginName);
        notification.addAction(NotificationAction.createSimple(message, () -> {
          // mark as removed from fileTypeOld and associated with fileTypeNew
          ApplicationManager.getApplication().runWriteAction(() -> {
            myRemovedMappingTracker.add(matcher, oldFileType.getName(), true);
            FileTypeManager.getInstance().associate(resolvedFileType, matcher);
          });
          notification.expire();
          String m = FileTypesBundle.message("dialog.message.file.pattern.was.assigned.to", matcher.getPresentableString(), resolvedDisplayName);
          showReassignedInfoNotification(project, m);
        }));
      }
      String revertMessage = result.oldPluginName == null ? FileTypesBundle.message("notification.content.revert.reassign", oldDisplayName) :
                             FileTypesBundle.message("notification.content.revert.reassign.from.plugin", oldDisplayName, result.oldPluginName);
      notification.addAction(NotificationAction.createSimple(revertMessage, () -> {
        // mark as removed from fileTypeNew and associated with fileTypeOld
        ApplicationManager.getApplication().runWriteAction(() -> {
          myRemovedMappingTracker.add(matcher, resolvedFileType.getName(), true);
          FileTypeManager.getInstance().associate(oldFileType, matcher);
        });
        notification.expire();
        String m = FileTypesBundle.message("dialog.message.file.pattern.was.reassigned.back.to", matcher.getPresentableString(), oldDisplayName);
        showReassignedInfoNotification(project, m);
      }));
      if (!oldFileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", oldDisplayName),
                                                               () -> editFileType(project, oldFileType)));
      }
      if (!resolvedFileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", resolvedDisplayName),
                                                               () -> editFileType(project, resolvedFileType)));
      }
      Notifications.Bus.notify(notification, project);
    }, project == null ? ApplicationManager.getApplication().getDisposed() : project.getDisposed());
  }

  private static void showReassignedInfoNotification(@Nullable Project project, @NotNull @NlsContexts.NotificationContent String message) {
    Notification confirmNotification = new Notification(
      NotificationGroup.createIdWithTitle("dialog.title.pattern.reassigned", FileTypesBundle.message("dialog.title.pattern.reassigned")),
      FileTypesBundle.message("dialog.title.pattern.reassigned"),
      message,
      NotificationType.INFORMATION, null);
    Notifications.Bus.notify(confirmNotification, project);
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
