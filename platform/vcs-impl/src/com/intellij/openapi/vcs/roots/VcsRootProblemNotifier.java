/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Searches for Vcs roots problems via {@link VcsRootErrorsFinder} and notifies about them.
 */
public class VcsRootProblemNotifier {

  public static final Function<VcsRootError, String> PATH_FROM_ROOT_ERROR = VcsRootError::getMapping;

  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration mySettings;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final ProjectFileIndex myProjectFileIndex;

  @NotNull private final Set<String> myReportedUnregisteredRoots;

  @Nullable private Notification myNotification;
  @NotNull private final Object NOTIFICATION_LOCK = new Object();

  public static VcsRootProblemNotifier getInstance(@NotNull Project project) {
    return new VcsRootProblemNotifier(project);
  }

  private VcsRootProblemNotifier(@NotNull Project project) {
    myProject = project;
    mySettings = VcsConfiguration.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(project);
    myProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myReportedUnregisteredRoots = new HashSet<>(mySettings.IGNORED_UNREGISTERED_ROOTS);
  }

  public void rescanAndNotifyIfNeeded() {
    Collection<VcsRootError> errors = scan();
    if (errors.isEmpty()) {
      synchronized (NOTIFICATION_LOCK) {
        expireNotification();
      }
      return;
    }

    Collection<VcsRootError> importantUnregisteredRoots = getImportantUnregisteredMappings(errors);
    Collection<VcsRootError> invalidRoots = getInvalidRoots(errors);

    List<String> unregRootPaths = ContainerUtil.map(importantUnregisteredRoots, PATH_FROM_ROOT_ERROR);
    if (invalidRoots.isEmpty() && (importantUnregisteredRoots.isEmpty() || myReportedUnregisteredRoots.containsAll(unregRootPaths))) {
      return;
    }
    myReportedUnregisteredRoots.addAll(unregRootPaths);

    String title = makeTitle(importantUnregisteredRoots, invalidRoots);
    String description = makeDescription(importantUnregisteredRoots, invalidRoots);

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      NotificationListener listener = new MyNotificationListener(myProject, mySettings, myVcsManager, importantUnregisteredRoots);
      VcsNotifier notifier = VcsNotifier.getInstance(myProject);
      myNotification = invalidRoots.isEmpty()
                       ? notifier.notifyMinorInfo(title, description, listener)
                       : notifier.notifyError(title, description, listener);
    }
  }

  private boolean isUnderOrAboveProjectDir(@NotNull String mapping) {
    String projectDir = ObjectUtils.assertNotNull(myProject.getBasePath());
    return mapping.equals(VcsDirectoryMapping.PROJECT_CONSTANT) ||
           FileUtil.isAncestor(projectDir, mapping, false) ||
           FileUtil.isAncestor(mapping, projectDir, false);
  }

  private boolean isIgnoredOrExcluded(@NotNull String mapping) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping);
    return file != null && (myChangeListManager.isIgnoredFile(file) || myProjectFileIndex.isExcluded(file));
  }

  private void expireNotification() {
    if (myNotification != null) {
      final Notification notification = myNotification;
      ApplicationManager.getApplication().invokeLater(notification::expire);

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
    Function<VcsRootError, String> rootToDisplayableString = rootError -> {
      if (rootError.getMapping().equals(VcsDirectoryMapping.PROJECT_CONSTANT)) {
        return StringUtil.escapeXml(rootError.getMapping());
      }
      return FileUtil.toSystemDependentName(rootError.getMapping());
    };

    StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        VcsRootError rootError = invalidRoots.iterator().next();
        String vcsName = rootError.getVcsKey().getName();
        description.append(String.format("The directory %s is registered as a %s root, but no %s repositories were found there.",
                                         rootToDisplayableString.fun(rootError), vcsName, vcsName));
      }
      else {
        description.append("The following directories are registered as VCS roots, but they are not: <br/>" +
                           StringUtil.join(invalidRoots, rootToDisplayableString, "<br/>"));
      }
      description.append("<br/>");
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        VcsRootError unregisteredRoot = unregisteredRoots.iterator().next();
        description.append(String.format("The directory %s is under %s, but is not registered in the Settings.",
                                         rootToDisplayableString.fun(unregisteredRoot), unregisteredRoot.getVcsKey().getName()));
      }
      else {
        description.append("The following directories are roots of VCS repositories, but they are not registered in the Settings: <br/>" +
                           StringUtil.join(unregisteredRoots, rootToDisplayableString, "<br/>"));
      }
      description.append("<br/>");
    }

    String add = invalidRoots.isEmpty() ? "<a href='add'>Add " + pluralize("root", unregisteredRoots.size()) + "</a>&nbsp;&nbsp;" : "";
    String configure = "<a href='configure'>Configure</a>";
    String ignore = invalidRoots.isEmpty() ? "&nbsp;&nbsp;<a href='ignore'>Ignore</a>" : "";
    description.append(add + configure + ignore);

    return description.toString();
  }

  @NotNull
  private static String makeTitle(@NotNull Collection<VcsRootError> unregisteredRoots, @NotNull Collection<VcsRootError> invalidRoots) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = "Invalid VCS root " + pluralize("mapping", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      title = "Unregistered VCS " + pluralize("root", unregisteredRoots.size()) + " detected";
    }
    else {
      title = "VCS root configuration problems";
    }
    return title;
  }

  @NotNull
  private List<VcsRootError> getImportantUnregisteredMappings(@NotNull Collection<VcsRootError> errors) {
    return ContainerUtil.filter(errors, error -> {
      String mapping = error.getMapping();
      return error.getType() == VcsRootError.Type.UNREGISTERED_ROOT && isUnderOrAboveProjectDir(mapping) && !isIgnoredOrExcluded(mapping);
    });
  }

  @NotNull
  private static Collection<VcsRootError> getInvalidRoots(@NotNull Collection<VcsRootError> errors) {
    return ContainerUtil.filter(errors, error -> error.getType() == VcsRootError.Type.EXTRA_MAPPING);
  }

  private static class MyNotificationListener extends NotificationListener.Adapter {

    @NotNull private final Project myProject;
    @NotNull private final VcsConfiguration mySettings;
    @NotNull private final ProjectLevelVcsManager myVcsManager;
    @NotNull private final Collection<VcsRootError> myImportantUnregisteredRoots;

    private MyNotificationListener(@NotNull Project project,
                                   @NotNull VcsConfiguration settings,
                                   @NotNull ProjectLevelVcsManager vcsManager,
                                   @NotNull Collection<VcsRootError> importantUnregisteredRoots) {
      myProject = project;
      mySettings = settings;
      myVcsManager = vcsManager;
      myImportantUnregisteredRoots = importantUnregisteredRoots;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getDescription().equals("configure") && !myProject.isDisposed()) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ActionsBundle.message("group.VcsGroup.text"));
        Collection<VcsRootError> errorsAfterPossibleFix = getInstance(myProject).scan();
        if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
          notification.expire();
        }
      }
      else if (event.getDescription().equals("ignore")) {
        mySettings.addIgnoredUnregisteredRoots(ContainerUtil.map(myImportantUnregisteredRoots, PATH_FROM_ROOT_ERROR));
        notification.expire();
      }
      else if (event.getDescription().equals("add")) {
        List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
        for (VcsRootError root : myImportantUnregisteredRoots) {
          mappings = VcsUtil.addMapping(mappings, root.getMapping(), root.getVcsKey().getName());
        }
        myVcsManager.setDirectoryMappings(mappings);
      }
    }
  }
}
