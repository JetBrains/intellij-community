// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

class ConflictingFileTypeMappingTracker {
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
                                               @Nullable FileTypeManagerImpl.FileTypeWithDescriptor oldFtd,
                                               @NotNull FileTypeManagerImpl.FileTypeWithDescriptor newFtd) {
    FileType oldFileType = oldFtd == null ? null : oldFtd.fileType;
    FileType newFileType = newFtd.fileType;
    if (oldFileType == null || oldFileType.equals(newFileType) || oldFileType instanceof AbstractFileType) {
      // no conflict really
      return new ResolveConflictResult(ObjectUtils.notNull(oldFtd, newFtd), "", "", true);
    }

    ResolveConflictResult result = resolveConflict(matcher, oldFtd, newFtd);
    // notify about only real conflicts between two same-league plugins
    if (!result.approved) {
      if (oldFtd.fileType.equals(newFileType)) {
        throw new IllegalArgumentException("expected different file types but got "+result.resolved);
      }

      showConflictNotification(null, matcher, oldFtd, result);
    }
    return result;
  }

  static class ResolveConflictResult {
    final @NotNull FileTypeManagerImpl.FileTypeWithDescriptor resolved;
    final @NotNull @Nls String notification;
    final @NotNull @Nls String explanation;
    final boolean approved;

    ResolveConflictResult(@NotNull FileTypeManagerImpl.FileTypeWithDescriptor resolved,
                          @NotNull @Nls String notification,
                          @NotNull @Nls String explanation,
                          boolean approved) {
      this.resolved = resolved;
      this.notification = notification;
      this.explanation = explanation;
      this.approved = approved;
    }

    @Override
    public String toString() {
      return "ResolveConflictResult: resolved="+resolved+"; explanation='"+explanation+"'; notification='" + notification+"'; approved="+approved;
    }
  }

  @NotNull
  @VisibleForTesting
  static ResolveConflictResult resolveConflict(@NotNull FileNameMatcher matcher,
                                               @NotNull FileTypeManagerImpl.FileTypeWithDescriptor oldFtd,
                                               @NotNull FileTypeManagerImpl.FileTypeWithDescriptor newFtd) {
    assert !oldFtd.equals(newFtd) : oldFtd;
    if (newFtd.pluginDescriptor.isBundled() &&
        (!oldFtd.pluginDescriptor.isBundled() || isCorePlugin(newFtd.pluginDescriptor) && !isCorePlugin(oldFtd.pluginDescriptor))) {
      FileTypeManagerImpl.FileTypeWithDescriptor ftd = newFtd;
      newFtd = oldFtd;
      oldFtd = ftd;
    }
    // now the bundled or core plugin, if any, is stored in oldFtd
    PluginDescriptor oldPlugin = oldFtd.pluginDescriptor;
    PluginDescriptor newPlugin = newFtd.pluginDescriptor;
    FileType oldFileType = oldFtd.fileType;
    FileType newFileType = newFtd.fileType;
    // do not show notification if the new plugin reassigned core or bundled plugin
    String oldPluginName = oldPlugin.isBundled() ? "bundled" : oldPlugin.getName();
    String explanation = FileTypesBundle.message("notification.content.file.type.reassigned.explanation", matcher.getPresentableString());
    // override core unconditionally
    if (!newPlugin.isBundled() || isCorePlugin(oldPlugin) && !isCorePlugin(newPlugin)) {
      boolean approved = oldPlugin.isBundled();
      // new plugin overrides pattern
      String message = FileTypesBundle.message("notification.content.file.type.reassigned.plugin", matcher.getPresentableString(), oldPluginName, newFileType.getDisplayName(), newPlugin.getName());
      return new ResolveConflictResult(newFtd, message, explanation, approved);
    }
    if (oldFileType == NativeFileType.INSTANCE) {
      // somebody overridden NativeFileType extension with their own type, which is always good
      String message = FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), newFileType.getDisplayName());
      return new ResolveConflictResult(newFtd, message, explanation, true);
    }
    
    // if both bundled, should win the bundle from other vendor as more specific
    // see the case "Image" plugin vs "Adobe Photoshop" from Android
    boolean isOldJetBrains = PluginManagerCore.isVendorJetBrains(StringUtil.notNullize(oldPlugin.getVendor()));
    boolean isNewJetBrains = PluginManagerCore.isVendorJetBrains(StringUtil.notNullize(newPlugin.getVendor()));
    if (isOldJetBrains != isNewJetBrains) {
      FileTypeManagerImpl.FileTypeWithDescriptor result = isOldJetBrains ? newFtd : oldFtd;
      String message = FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), result.fileType.getDisplayName());
      return new ResolveConflictResult(result, message, explanation, true);
    }

    /* ? wild guess: two bundled file types */
    String message = FileTypesBundle.message("notification.content.file.pattern.was.reassigned.to", matcher.getPresentableString(), oldFileType.getDisplayName());
    // prefer old file type to avoid notification about file type reassignments twice
    return new ResolveConflictResult(oldFtd, message, explanation, false);
  }

  private static boolean isCorePlugin(@NotNull PluginDescriptor descriptor) {
    return PluginManagerCore.CORE_ID.equals(descriptor.getPluginId());
  }

  private final RemovedMappingTracker myRemovedMappingTracker;

  ConflictingFileTypeMappingTracker(@NotNull RemovedMappingTracker removedMappingTracker) {
    myRemovedMappingTracker = removedMappingTracker;
  }

  /**
   * there is a conflict: a matcher belongs to several file types, so
   * {@code matcher} removed from {@code oldFileType} and assigned to {@code result.resolved}
   */
  private void showConflictNotification(@Nullable Project project,
                                        @NotNull FileNameMatcher matcher,
                                        @NotNull FileTypeManagerImpl.FileTypeWithDescriptor oldFtd,
                                        @NotNull ResolveConflictResult result) {
    FileType resolvedFileType = result.resolved.fileType;
    @Nls String notificationText = result.notification;
    String oldDisplayName = oldFtd.fileType.getDisplayName();
    String resolvedDisplayName = resolvedFileType.getDisplayName();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("File type conflict");
      Notification notification = group.createNotification(
        notificationText,
        result.explanation,
        NotificationType.INFORMATION);
      String message =
      result.resolved.pluginDescriptor.isBundled() ? FileTypesBundle.message("notification.content.conflict.confirm.reassign", resolvedDisplayName) :
      FileTypesBundle.message("notification.content.conflict.confirm.reassign.from.plugin", resolvedDisplayName, result.resolved.pluginDescriptor.getName());
      notification.addAction(NotificationAction.createSimple(message, () -> {
        // mark as removed from fileTypeOld and associated with fileTypeNew
        ApplicationManager.getApplication().runWriteAction(() -> {
          myRemovedMappingTracker.add(matcher, oldFtd.getName(), true);
          ((FileTypeManagerImpl)FileTypeManager.getInstance()).associate(result.resolved, matcher, true);
        });
        notification.expire();
        String m = FileTypesBundle.message("dialog.message.file.pattern.was.assigned.to", matcher.getPresentableString(), resolvedDisplayName);
        showReassignedInfoNotification(project, m);
      }));
      String revertMessage =
      oldFtd.pluginDescriptor.isBundled() ? FileTypesBundle.message("notification.content.revert.reassign", oldDisplayName) :
      FileTypesBundle.message("notification.content.revert.reassign.from.plugin", oldDisplayName, oldFtd.pluginDescriptor.getName());
      notification.addAction(NotificationAction.createSimple(revertMessage, () -> {
        // mark as removed from fileTypeNew and associated with fileTypeOld
        ApplicationManager.getApplication().runWriteAction(() -> {
          myRemovedMappingTracker.add(matcher, resolvedFileType.getName(), true);
          ((FileTypeManagerImpl)FileTypeManager.getInstance()).associate(oldFtd, matcher, true);
        });
        notification.expire();
        String m = FileTypesBundle.message("dialog.message.file.pattern.was.reassigned.back.to", matcher.getPresentableString(), oldDisplayName);
        showReassignedInfoNotification(project, m);
      }));
      if (!oldFtd.fileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", oldDisplayName),
                                                               () -> editFileType(project, oldFtd.fileType)));
      }
      if (!resolvedFileType.isReadOnly()) {
        notification.addAction(NotificationAction.createSimple(FileTypesBundle.message("notification.content.edit", resolvedDisplayName),
                                                               () -> editFileType(project, resolvedFileType)));
      }
      Notifications.Bus.notify(notification, project);
    }, project == null ? ApplicationManager.getApplication().getDisposed() : project.getDisposed());
  }

  private static void showReassignedInfoNotification(@Nullable Project project, @NotNull @NlsContexts.NotificationContent String message) {
    new Notification(
      NotificationGroup.getGroupTitle("Pattern reassigned"),
      FileTypesBundle.message("dialog.title.pattern.reassigned"),
      message,
      NotificationType.INFORMATION
    ).notify(project);
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
