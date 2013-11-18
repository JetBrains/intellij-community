/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.roots;

import com.intellij.idea.ActionsBundle;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Searches for Vcs roots problems via {@link VcsRootErrorsFinder} and notifies about them.
 *
 * @author Nadya Zabrodina
 */
public class VcsRootProblemNotifier {

  private final @NotNull Project myProject;
  private final @NotNull VcsConfiguration mySettings;

  private @Nullable Notification myNotification;
  private final @NotNull Object NOTIFICATION_LOCK = new Object();

  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION = new NotificationGroup(
    "Vcs Important Messages", NotificationDisplayType.STICKY_BALLOON, true);
  public static final NotificationGroup MINOR_NOTIFICATION = new NotificationGroup(
    "Vcs Minor Notifications", NotificationDisplayType.BALLOON, true);

  public static VcsRootProblemNotifier getInstance(@NotNull Project project) {
    return new VcsRootProblemNotifier(project);
  }

  private VcsRootProblemNotifier(@NotNull Project project) {
    myProject = project;
    mySettings = VcsConfiguration.getInstance(myProject);
  }

  public void rescanAndNotifyIfNeeded() {
    if (!mySettings.SHOW_VCS_ERROR_NOTIFICATIONS) {
      return;
    }

    Collection<VcsRootError> errors = scan();
    if (errors.isEmpty()) {
      synchronized (NOTIFICATION_LOCK) {
        expireNotification();
      }
      return;
    }

    Collection<VcsRootError> unregisteredRoots = getUnregisteredRoots(errors);
    Collection<VcsRootError> invalidRoots = getInvalidRoots(errors);

    String title = makeTitle(unregisteredRoots, invalidRoots);
    String description = makeDescription(unregisteredRoots, invalidRoots);

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      NotificationGroup notificationGroup = invalidRoots.isEmpty() ? MINOR_NOTIFICATION : IMPORTANT_ERROR_NOTIFICATION;
      NotificationType notificationType = invalidRoots.isEmpty() ? INFORMATION : ERROR;
      myNotification = notificationGroup.createNotification(title, description, notificationType,
                                                            new MyNotificationListener(myProject, mySettings));
      myNotification.notify(myProject);
    }
  }

  private void expireNotification() {
    if (myNotification != null) {
      final Notification notification = myNotification;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          notification.expire();
        }
      });

      myNotification = null;
    }
  }

  @NotNull
  private Collection<VcsRootError> scan() {
    return new VcsRootErrorsFinder(myProject).find();
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  @NotNull
  private static String makeDescription(@NotNull Collection<VcsRootError> unregisteredRoots,
                                        @NotNull Collection<VcsRootError> invalidRoots) {
    Function<VcsRootError, String> rootToDisplayableString = new Function<VcsRootError, String>() {
      @Override
      public String fun(VcsRootError rootError) {
        if (rootError.getMapping().equals(VcsDirectoryMapping.PROJECT_CONSTANT)) {
          return StringUtil.escapeXml(rootError.getMapping());
        }
        return FileUtil.toSystemDependentName(rootError.getMapping());
      }
    };

    StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        VcsRootError rootError = invalidRoots.iterator().next();
        description
          .append("The directory " +
                  rootToDisplayableString.fun(rootError) +
                  " is registered as a " +
                  rootError.getVcsKey().getName() +
                  " root, " +
                  "but no " +
                  rootError.getVcsKey().getName() +
                  " repositories were found there.");
      }
      else {
        description.append("The following directories are registered as Vcs roots, but they are not: <br/>" +
                           StringUtil.join(invalidRoots, rootToDisplayableString, ", "));
      }
      description.append("<br/>");
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        VcsRootError unregisteredRoot = unregisteredRoots.iterator().next();
        description
          .append("The directory " +
                  rootToDisplayableString.fun(unregisteredRoot) +
                  " is under " +
                  unregisteredRoot.getVcsKey().getName() +
                  ", " +
                  "but is not registered in the Settings.");
      }
      else {
        description.append("The following directories are roots of Vcs repositories, but they are not registered in the Settings: <br/>" +
                           StringUtil.join(unregisteredRoots, rootToDisplayableString, ", "));
      }
      description.append("<br/>");
    }

    description.append("<a href='configure'>Configure</a>&nbsp;&nbsp;<a href='ignore'>Ignore VCS root errors</a>");

    return description.toString();
  }

  @NotNull
  private static String makeTitle(@NotNull Collection<VcsRootError> unregisteredRoots, @NotNull Collection<VcsRootError> invalidRoots) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = "Invalid Vcs root " + pluralize("mapping", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      title = "Unregistered Vcs " + pluralize("root", unregisteredRoots.size()) + " detected";
    }
    else {
      title = "Vcs root configuration problems";
    }
    return title;
  }

  @NotNull
  private static Collection<VcsRootError> getUnregisteredRoots(@NotNull Collection<VcsRootError> errors) {
    return filterErrorsByType(errors, VcsRootError.Type.UNREGISTERED_ROOT);
  }

  @NotNull
  private static Collection<VcsRootError> getInvalidRoots(@NotNull Collection<VcsRootError> errors) {
    return filterErrorsByType(errors, VcsRootError.Type.EXTRA_MAPPING);
  }

  @NotNull
  private static Collection<VcsRootError> filterErrorsByType(@NotNull Collection<VcsRootError> errors, @NotNull VcsRootError.Type type) {
    Collection<VcsRootError> roots = new ArrayList<VcsRootError>();
    for (VcsRootError error : errors) {
      if (error.getType() == type) {
        roots.add(error);
      }
    }
    return roots;
  }

  private static class MyNotificationListener implements NotificationListener {

    @NotNull private final Project myProject;
    @NotNull private final VcsConfiguration mySettings;

    private MyNotificationListener(@NotNull Project project, @NotNull VcsConfiguration settings) {
      myProject = project;
      mySettings = settings;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (event.getDescription().equals("configure") && !myProject.isDisposed()) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ActionsBundle.message("group.VcsGroup.text"));
          Collection<VcsRootError> errorsAfterPossibleFix = getInstance(myProject).scan();
          if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
            notification.expire();
          }
        }
        else if (event.getDescription().equals("ignore")) {
          mySettings.SHOW_VCS_ERROR_NOTIFICATIONS = false;
          notification.expire();
        }
      }
    }
  }
}
