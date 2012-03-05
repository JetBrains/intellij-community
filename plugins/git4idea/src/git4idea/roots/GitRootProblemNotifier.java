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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import git4idea.GitVcs;
import git4idea.PlatformFacade;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Searches for Git roots problems via {@link GitRootErrorsFinder} and notifies about them.
 *
 * @author Kirill Likhodedov
 */
public class GitRootProblemNotifier {

  private final @NotNull Project myProject;
  private final @NotNull PlatformFacade myPlatformFacade;

  public static GitRootProblemNotifier getInstance(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    return new GitRootProblemNotifier(project, platformFacade);
  }

  public GitRootProblemNotifier(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  public void rescanAndNotifyIfNeeded() {
    Collection<VcsRootError> errors = new GitRootErrorsFinder(myProject, myPlatformFacade).find();
    if (errors.isEmpty()) {
      return;
    }

    Collection<VirtualFile> unregisteredRoots = getUnregisteredRoots(errors);
    Collection<VirtualFile> invalidRoots = getInvalidRoots(errors);

    String title = makeTitle(unregisteredRoots, invalidRoots);
    String description = makeDescription(unregisteredRoots, invalidRoots);

    myPlatformFacade.getNotificator(myProject).notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, description,
                                                      NotificationType.ERROR, new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("configure")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, "Version Control");
        }
      }
    });
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  private static String makeDescription(Collection<VirtualFile> unregisteredRoots, Collection<VirtualFile> invalidRoots) {
    Function<VirtualFile, String> rootToString = new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile virtualFile) {
        return FileUtil.toSystemDependentName(virtualFile.getPath());
      }
    };

    StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        description.append("The directory " + invalidRoots.iterator().next() + " is registered as a Git root, " +
                           "but it doesn't have .git directory inside.");
      }
      else {
        description.append("The following directories are registered as Git roots, but they don't have .git directotires inside: <br/>" +
                           StringUtil.join(invalidRoots, rootToString, ", "));
      }
      description.append("<br/>");
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        description.append("The directory " + unregisteredRoots.iterator().next() + " is under Git, " +
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

  private static String makeTitle(Collection<VirtualFile> unregisteredRoots, Collection<VirtualFile> invalidRoots) {
    String title;
    String roots = pluralize("root", invalidRoots.size());
    if (unregisteredRoots.isEmpty()) {
      title = "Invalid Git " + roots;
    }
    else if (invalidRoots.isEmpty()) {
      title = "Unregistered Git " + roots + " detected";
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

}
