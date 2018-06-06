// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.escapeXml;
import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static com.intellij.openapi.vcs.VcsDirectoryMapping.PROJECT_CONSTANT;
import static com.intellij.openapi.vcs.VcsRootError.Type.UNREGISTERED_ROOT;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;

/**
 * Searches for Vcs roots problems via {@link VcsRootErrorsFinder} and notifies about them.
 */
public class VcsRootProblemNotifier {
  private static final Logger LOG = Logger.getInstance(VcsRootProblemNotifier.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration mySettings;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final ProjectFileIndex myProjectFileIndex;

  // unregistered roots reported during this session but not explicitly ignored
  @NotNull private final Set<String> myReportedUnregisteredRoots;

  @Nullable private Notification myNotification;
  @NotNull private final Object NOTIFICATION_LOCK = new Object();

  @NotNull private final Function<VcsRootError, String> ROOT_TO_PRESENTABLE = rootError -> {
    if (rootError.getMapping().equals(PROJECT_CONSTANT)) return escapeXml(rootError.getMapping());
    return getPresentableMapping(rootError.getMapping());
  };

  public static VcsRootProblemNotifier getInstance(@NotNull Project project) {
    return new VcsRootProblemNotifier(project);
  }

  private VcsRootProblemNotifier(@NotNull Project project) {
    myProject = project;
    mySettings = VcsConfiguration.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(project);
    myProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myReportedUnregisteredRoots = new HashSet<>();
  }

  public void rescanAndNotifyIfNeeded() {
    Collection<VcsRootError> errors = scan();
    if (errors.isEmpty()) {
      synchronized (NOTIFICATION_LOCK) {
        expireNotification();
      }
      return;
    }
    LOG.debug("Following errors detected: " + errors);

    Collection<VcsRootError> importantUnregisteredRoots = getImportantUnregisteredMappings(errors);
    Collection<VcsRootError> invalidRoots = getInvalidRoots(errors);

    String title;
    String description;
    NotificationAction[] notificationActions;

    if (Registry.is("vcs.root.auto.add") && !areThereExplicitlyIgnoredRoots(errors)) {
      if (invalidRoots.isEmpty() && importantUnregisteredRoots.isEmpty()) return;

      LOG.info("Auto-registered following mappings: " + importantUnregisteredRoots);
      addMappings(importantUnregisteredRoots);

      // Register the single root equal to the project dir silently, without any notification
      if (invalidRoots.isEmpty() &&
          importantUnregisteredRoots.size() == 1 &&
          FileUtil.pathsEqual(notNull(getFirstItem(importantUnregisteredRoots)).getMapping(), myProject.getBasePath())) {
        return;
      }

      title = makeTitle(importantUnregisteredRoots, invalidRoots, true);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);
      notificationActions = new NotificationAction[]{getConfigureNotificationAction()};
    }
    else {
      // Don't report again, if these roots were already reported
      List<String> unregRootPaths = map(importantUnregisteredRoots, VcsRootError::getMapping);
      if (invalidRoots.isEmpty() && (importantUnregisteredRoots.isEmpty() || myReportedUnregisteredRoots.containsAll(unregRootPaths))) {
        return;
      }
      myReportedUnregisteredRoots.addAll(unregRootPaths);

      title = makeTitle(importantUnregisteredRoots, invalidRoots, false);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);

      NotificationAction enableIntegration = NotificationAction.create("Enable Integration",
                                                                       (event, notification) -> addMappings(importantUnregisteredRoots));
      NotificationAction ignoreAction = NotificationAction.create("Ignore", (event, notification) -> {
        mySettings.addIgnoredUnregisteredRoots(map(importantUnregisteredRoots, VcsRootError::getMapping));
        notification.expire();
      });
      notificationActions = new NotificationAction[]{enableIntegration, getConfigureNotificationAction(), ignoreAction};
    }

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      VcsNotifier notifier = VcsNotifier.getInstance(myProject);

      myNotification = invalidRoots.isEmpty()
                       ? notifier.notifyMinorInfo(title, description, notificationActions)
                       : notifier.notifyError(title, description, getConfigureNotificationAction());
    }
  }

  @NotNull
  private NotificationAction getConfigureNotificationAction() {
    return NotificationAction.create("Configure...", (event, notification) -> {
      if (!myProject.isDisposed()) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ActionsBundle.message("group.VcsGroup.text"));
        Collection<VcsRootError> errorsAfterPossibleFix = getInstance(myProject).scan();
        if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
          notification.expire();
        }
      }
    });
  }

  private void addMappings(Collection<VcsRootError> importantUnregisteredRoots) {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    for (VcsRootError root : importantUnregisteredRoots) {
      mappings = VcsUtil.addMapping(mappings, root.getMapping(), root.getVcsKey().getName());
    }
    myVcsManager.setDirectoryMappings(mappings);
  }

  private boolean isUnderOrAboveProjectDir(@NotNull String mapping) {
    String projectDir = ObjectUtils.assertNotNull(myProject.getBasePath());
    return mapping.equals(PROJECT_CONSTANT) ||
           FileUtil.isAncestor(projectDir, mapping, false) ||
           FileUtil.isAncestor(mapping, projectDir, false);
  }

  private boolean isIgnoredOrExcludedPath(@NotNull String mapping) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping);
    return file != null && (myChangeListManager.isIgnoredFile(file) || ReadAction.compute(() -> myProjectFileIndex.isExcluded(file)));
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
  private String makeDescription(@NotNull Collection<VcsRootError> unregisteredRoots,
                                 @NotNull Collection<VcsRootError> invalidRoots) {
    StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        VcsRootError rootError = invalidRoots.iterator().next();
        String vcsName = rootError.getVcsKey().getName();
        description.append(String.format("The directory %s is registered as a %s root, but no %s repositories were found there.",
                                         ROOT_TO_PRESENTABLE.fun(rootError), vcsName, vcsName));
      }
      else {
        description.append("The following directories are registered as VCS roots, but they are not: <br/>" +
                           joinRootsForPresentation(invalidRoots));
      }
      description.append("<br/>");
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        VcsRootError unregisteredRoot = unregisteredRoots.iterator().next();
        description.append(ROOT_TO_PRESENTABLE.fun(unregisteredRoot));
      }
      else {
        description.append(joinRootsForPresentation(unregisteredRoots));
      }
    }
    return description.toString();
  }

  @NotNull
  private String joinRootsForPresentation(@NotNull Collection<VcsRootError> errors) {
    return StringUtil.join(sorted(errors, (root1, root2) -> {
      if (root1.getMapping().equals(PROJECT_CONSTANT)) return -1;
      if (root2.getMapping().equals(PROJECT_CONSTANT)) return 1;
      return root1.getMapping().compareTo(root2.getMapping());
    }), ROOT_TO_PRESENTABLE, "<br/>");
  }

  @NotNull
  private static String makeTitle(@NotNull Collection<VcsRootError> unregisteredRoots,
                                  @NotNull Collection<VcsRootError> invalidRoots,
                                  boolean rootsAlreadyAdded) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = "Invalid VCS root " + pluralize("mapping", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      String vcs = getVcsName(unregisteredRoots);
      String repository = pluralize("Repository", unregisteredRoots.size());
      title = String.format(rootsAlreadyAdded ? "%s Integration Enabled for %s" : "%s %s Found", vcs, repository);
    }
    else {
      title = "VCS root configuration problems";
    }
    return title;
  }

  private static String getVcsName(Collection<VcsRootError> roots) {
    String result = null;
    for (VcsRootError root : roots) {
      String vcsName = root.getVcsKey().getName();
      if (result == null) {
        result = vcsName;
      }
      else if (!result.equals(vcsName)) {
        return "VCS";
      }
    }
    return result;
  }

  @NotNull
  private List<VcsRootError> getImportantUnregisteredMappings(@NotNull Collection<VcsRootError> errors) {
    return filter(errors, error -> {
      String mapping = error.getMapping();
      return error.getType() == UNREGISTERED_ROOT &&
             isUnderOrAboveProjectDir(mapping) &&
             !isIgnoredOrExcludedPath(mapping) &&
             !mySettings.isIgnoredUnregisteredRoot(mapping);
    });
  }

  private boolean areThereExplicitlyIgnoredRoots(Collection<VcsRootError> allErrors) {
    return exists(allErrors, it -> it.getType() == UNREGISTERED_ROOT && mySettings.isIgnoredUnregisteredRoot(it.getMapping()));
  }

  @NotNull
  private static Collection<VcsRootError> getInvalidRoots(@NotNull Collection<VcsRootError> errors) {
    return filter(errors, error -> error.getType() == VcsRootError.Type.EXTRA_MAPPING);
  }


  @VisibleForTesting
  @NotNull
  String getPresentableMapping(@NotNull String mapping) {
    String relativePath = null;
    String projectDir = myProject.getBasePath();
    if (projectDir != null && FileUtil.isAncestor(projectDir, mapping, true)) {
      relativePath = "<Project>/" + FileUtil.getRelativePath(projectDir, mapping, File.separatorChar);
    }
    if (relativePath == null) {
      relativePath = FileUtil.getLocationRelativeToUserHome(toSystemDependentName(mapping));
    }
    return StringUtil.shortenPathWithEllipsis(escapeXml(relativePath), 30, true);
  }
}
