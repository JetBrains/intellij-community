/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.project.ProjectUtil.guessProjectDir;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static com.intellij.openapi.vcs.VcsDirectoryMapping.PROJECT_CONSTANT;
import static com.intellij.openapi.vcs.VcsRootError.Type.UNREGISTERED_ROOT;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Collections.singletonList;

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

  @NotNull private static final Function<VcsRootError, String> ROOT_TO_PRESENTABLE = rootError -> {
    if (rootError.getMapping().equals(PROJECT_CONSTANT)) return StringUtil.escapeXml(rootError.getMapping());
    return toSystemDependentName(rootError.getMapping());
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

    if (importantUnregisteredRoots.size() == 1) {
      VcsRootError singleUnregRoot = notNull(getFirstItem(importantUnregisteredRoots));
      String mappingPath = singleUnregRoot.getMapping();
      VirtualFile projectDir = guessProjectDir(myProject);
      Collection<VcsRootError> allUnregisteredRoots = filter(errors, it -> it.getType() == UNREGISTERED_ROOT);
      if (!myVcsManager.hasAnyMappings() &&
          allUnregisteredRoots.size() == 1 &&
          !myReportedUnregisteredRoots.contains(mappingPath) &&
          FileUtil.isAncestor(projectDir.getPath(), mappingPath, false) &&
          Registry.is("vcs.auto.add.single.root")) {
        VcsDirectoryMapping mapping = new VcsDirectoryMapping(mappingPath, singleUnregRoot.getVcsKey().getName());
        myVcsManager.setDirectoryMappings(singletonList(mapping));
        LOG.info("Added " + mapping.getVcs() + " root " + mapping + " as the only auto-detected root.");
        return;
      }
    }

    List<String> unregRootPaths = ContainerUtil.map(importantUnregisteredRoots, VcsRootError::getMapping);
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
  private static String makeDescription(@NotNull Collection<VcsRootError> unregisteredRoots,
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
        description.append(String.format("The directory %s is under %s, but is not registered in the Settings.",
                                         ROOT_TO_PRESENTABLE.fun(unregisteredRoot), unregisteredRoot.getVcsKey().getName()));
      }
      else {
        description.append("The following directories are roots of VCS repositories, but they are not registered in the Settings: <br/>" +
                           joinRootsForPresentation(unregisteredRoots));
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
  private static String joinRootsForPresentation(@NotNull Collection<VcsRootError> errors) {

    return StringUtil.join(sorted(errors, (root1, root2) -> {
      if (root1.getMapping().equals(PROJECT_CONSTANT)) return -1;
      if (root2.getMapping().equals(PROJECT_CONSTANT)) return 1;
      return root1.getMapping().compareTo(root2.getMapping());
    }), ROOT_TO_PRESENTABLE, "<br/>");
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
    return filter(errors, error -> {
      String mapping = error.getMapping();
      return error.getType() == UNREGISTERED_ROOT &&
             isUnderOrAboveProjectDir(mapping) &&
             !isIgnoredOrExcludedPath(mapping) &&
             !mySettings.isIgnoredUnregisteredRoot(mapping);
    });
  }

  @NotNull
  private static Collection<VcsRootError> getInvalidRoots(@NotNull Collection<VcsRootError> errors) {
    return filter(errors, error -> error.getType() == VcsRootError.Type.EXTRA_MAPPING);
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
        mySettings.addIgnoredUnregisteredRoots(ContainerUtil.map(myImportantUnregisteredRoots, VcsRootError::getMapping));
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
