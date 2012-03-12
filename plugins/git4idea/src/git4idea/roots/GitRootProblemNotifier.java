/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import git4idea.PlatformFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static git4idea.GitVcs.IMPORTANT_ERROR_NOTIFICATION;
import static git4idea.Notificator.createNotification;

/**
 * Searches for Git roots problems via {@link GitRootErrorsFinder} and notifies about them.
 *
 * @author Kirill Likhodedov
 */
public class GitRootProblemNotifier {

  private final @NotNull Project myProject;
  private final @NotNull PlatformFacade myPlatformFacade;

  private @Nullable Notification myNotification;
  private final @NotNull Object NOTIFICATION_LOCK = new Object();

  public static GitRootProblemNotifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitRootProblemNotifier.class);
  }

  // registered as a project service
  @SuppressWarnings("UnusedDeclaration")
  private GitRootProblemNotifier(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  public void rescanAndNotifyIfNeeded() {
    Collection<VcsRootError> errors = scan();
    if (errors.isEmpty()) {
      synchronized (NOTIFICATION_LOCK) {
        expireNotification();
      }
      return;
    }

    Collection<VirtualFile> unregisteredRoots = getUnregisteredRoots(errors);
    Collection<VirtualFile> invalidRoots = getInvalidRoots(errors);

    String title = makeTitle(unregisteredRoots, invalidRoots);
    String description = makeDescription(unregisteredRoots, invalidRoots);

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      myNotification = createNotification(IMPORTANT_ERROR_NOTIFICATION, title, description, ERROR, new MyNotificationListener(myProject));
      myPlatformFacade.getNotificator(myProject).notify(myNotification);
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
    return new GitRootErrorsFinder(myProject, myPlatformFacade).find();
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  private static String makeDescription(@NotNull Collection<VirtualFile> unregisteredRoots, @NotNull Collection<VirtualFile> invalidRoots) {
    Function<VirtualFile, String> rootToString = new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile virtualFile) {
        return FileUtil.toSystemDependentName(virtualFile.getPath());
      }
    };

    StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        description.append("The directory " + rootToString.fun(invalidRoots.iterator().next()) + " is registered as a Git root, " +
                           "but it is not.");
      }
      else {
        description.append("The following directories are registered as Git roots, but they are not: <br/>" +
                           StringUtil.join(invalidRoots, rootToString, ", "));
      }
      description.append("<br/>");
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        description.append("The directory " + rootToString.fun(unregisteredRoots.iterator().next()) + " is under Git, " +
                           "but is not registered in the Settings.");
      }
      else {
        description.append("The following directories are roots of Git repositories, but they are not registered in the Settings: <br/>" +
                           StringUtil.join(unregisteredRoots, rootToString, ", "));
      }
      description.append("<br/>");
    }

    description.append("<a href='configure'>Configure</a>");

    return description.toString();
  }

  @NotNull
  private static String makeTitle(@NotNull Collection<VirtualFile> unregisteredRoots, @NotNull Collection<VirtualFile> invalidRoots) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = "Invalid Git " + pluralize("root", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      title = "Unregistered Git " + pluralize("root", unregisteredRoots.size()) + " detected";
    }
    else {
      title = "Git root configuration problems";
    }
    return title;
  }

  @NotNull
  private static Collection<VirtualFile> getUnregisteredRoots(@NotNull Collection<VcsRootError> errors) {
    return filterErrorsByType(errors, VcsRootError.Type.UNREGISTERED_ROOT);
  }

  @NotNull
  private static Collection<VirtualFile> getInvalidRoots(@NotNull Collection<VcsRootError> errors) {
    return filterErrorsByType(errors, VcsRootError.Type.EXTRA_ROOT);
  }

  @NotNull
  private static Collection<VirtualFile> filterErrorsByType(@NotNull Collection<VcsRootError> errors, @NotNull VcsRootError.Type type) {
    Collection<VirtualFile> roots = new ArrayList<VirtualFile>();
    for (VcsRootError error : errors) {
      if (error.getType() == type) {
        roots.add(error.getRoot());
      }
    }
    return roots;
  }

  private static class MyNotificationListener implements NotificationListener {

    private final @NotNull Project myProject;

    private MyNotificationListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("configure")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ActionsBundle.message("group.VcsGroup.text"));
        Collection<VcsRootError> errorsAfterPossibleFix = GitRootProblemNotifier.getInstance(myProject).scan();
        if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
          notification.expire();
        }
      }
    }
  }
}
