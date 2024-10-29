// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.configurable.VcsMappingConfigurable;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.escapeXmlEntities;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.ROOTS_INVALID;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.ROOTS_REGISTERED;
import static com.intellij.openapi.vcs.VcsRootError.Type.UNREGISTERED_ROOT;
import static com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.ui.UIUtil.BR;

/**
 * Searches for Vcs roots problems via {@link VcsRootErrorsFinder} and notifies about them.
 */
@ApiStatus.Internal
public final class VcsRootProblemNotifier {
  private static final Logger LOG = Logger.getInstance(VcsRootProblemNotifier.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration mySettings;
  @NotNull private final ProjectLevelVcsManagerImpl myVcsManager;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final ProjectFileIndex myProjectFileIndex;

  // unregistered roots reported during this session but not explicitly ignored
  @NotNull private final Set<String> myReportedUnregisteredRoots;

  @Nullable private Notification myNotification;
  @NotNull private final Object NOTIFICATION_LOCK = new Object();

  @NotNull private final Function<VcsRootError, String> ROOT_TO_PRESENTABLE = rootError -> getPresentableMapping(rootError.getMapping());

  public static VcsRootProblemNotifier createInstance(@NotNull Project project) {
    return new VcsRootProblemNotifier(project);
  }

  private VcsRootProblemNotifier(@NotNull Project project) {
    myProject = project;
    mySettings = VcsConfiguration.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(project);
    myProjectFileIndex = ProjectFileIndex.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManagerImpl.getInstanceImpl(project);
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
    MAPPING_DETECTION_LOG.debug("Following errors detected: " + errors);

    Collection<VcsRootError> importantUnregisteredRoots = getImportantUnregisteredMappings(errors);
    Collection<VcsRootError> invalidRoots = getInvalidRoots(errors);

    String title;
    String description;
    NotificationAction[] notificationActions;

    if (Registry.is("vcs.root.auto.add") && !areThereExplicitlyIgnoredRoots(errors)) {
      if (invalidRoots.isEmpty() && importantUnregisteredRoots.isEmpty()) return;

      LOG.info("Auto-registered following mappings: " + importantUnregisteredRoots);
      addMappings(importantUnregisteredRoots, true);

      // Register the single root equal to the project dir silently, without any notification
      if (invalidRoots.isEmpty() &&
          importantUnregisteredRoots.size() == 1) {
        VcsRootError rootError = Objects.requireNonNull(getFirstItem(importantUnregisteredRoots));
        if (FileUtil.pathsEqual(rootError.getMapping().getDirectory(), myProject.getBasePath())) {
          return;
        }
      }

      // Don't display the notification about registered roots unless configured to do so (and unless there are invalid roots)
      if (invalidRoots.isEmpty() && !Registry.is("vcs.root.auto.add.nofity")) {
        return;
      }

      title = makeTitle(importantUnregisteredRoots, invalidRoots, true);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);
      notificationActions = new NotificationAction[]{getConfigureNotificationAction()};
    }
    else {
      // Don't report again, if these roots were already reported
      List<String> unregRootPaths = map(importantUnregisteredRoots, rootError -> rootError.getMapping().getDirectory());
      if (invalidRoots.isEmpty() && (importantUnregisteredRoots.isEmpty() || myReportedUnregisteredRoots.containsAll(unregRootPaths))) {
        return;
      }
      myReportedUnregisteredRoots.addAll(unregRootPaths);

      title = makeTitle(importantUnregisteredRoots, invalidRoots, false);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);

      NotificationAction enableIntegration = NotificationAction
        .create(VcsBundle.messagePointer("action.NotificationAction.VcsRootProblemNotifier.text.enable.integration"),
                (event, notification) -> addMappings(importantUnregisteredRoots, false));
      NotificationAction ignoreAction = NotificationAction
        .create(VcsBundle.messagePointer("action.NotificationAction.VcsRootProblemNotifier.text.ignore"), (event, notification) -> {
          mySettings.addIgnoredUnregisteredRoots(map(importantUnregisteredRoots, rootError -> rootError.getMapping().getDirectory()));
          notification.expire();
        });
      notificationActions = new NotificationAction[]{enableIntegration, getConfigureNotificationAction(), ignoreAction};
    }

    ProgressManager.checkCanceled();

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      VcsNotifier notifier = VcsNotifier.getInstance(myProject);

      myNotification = invalidRoots.isEmpty()
                       ? notifier.notifyMinorInfo(ROOTS_REGISTERED, title, description, notificationActions)
                       : notifier.notifyError(ROOTS_INVALID, title, description, getConfigureNotificationAction());
    }
  }

  @NotNull
  private NotificationAction getConfigureNotificationAction() {
    return NotificationAction.create(
      VcsBundle.messagePointer("action.NotificationAction.VcsRootProblemNotifier.text.configure"),
      (event, notification) -> {
        if (!myProject.isDisposed()) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, VcsMappingConfigurable.class);

          BackgroundTaskUtil.executeOnPooledThread(myProject, () -> {
            Collection<VcsRootError> errorsAfterPossibleFix = new VcsRootProblemNotifier(myProject).scan();
            if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
              notification.expire();
            }
          });
        }
      });
  }

  private void addMappings(Collection<? extends VcsRootError> importantUnregisteredRoots, boolean silently) {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    for (VcsRootError root : importantUnregisteredRoots) {
      mappings = VcsUtil.addMapping(mappings, root.getMapping());
    }
    if (silently) {
      myVcsManager.setAutoDirectoryMappings(mappings);
    }
    else {
      myVcsManager.setDirectoryMappings(mappings);
    }
  }

  private boolean isIgnoredOrExcludedPath(@NotNull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return false;
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
    return file != null && (myChangeListManager.isIgnoredFile(file) || ReadAction.compute(() -> myProjectFileIndex.isExcluded(file)));
  }

  private boolean isExplicitlyIgnoredPath(@NotNull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return false;
    return mySettings.isIgnoredUnregisteredRoot(mapping.getDirectory());
  }

  private boolean conflictsWithExistingMapping(@NotNull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return false;
    return exists(myVcsManager.getDirectoryMappings(), it -> {
      return Objects.equals(mapping.getDirectory(), it.getDirectory());
    });
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

  @NotNull
  private @NlsContexts.NotificationContent String makeDescription(@NotNull Collection<? extends VcsRootError> unregisteredRoots,
                                                                  @NotNull Collection<? extends VcsRootError> invalidRoots) {
    @Nls StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        VcsRootError rootError = invalidRoots.iterator().next();
        String vcsName = rootError.getMapping().getVcs();
        description.append(getInvalidRootDescriptionItem(rootError, vcsName));
      }
      else {
        description.append(VcsBundle.message("roots.the.following.directories.are.registered.as.vcs.roots.but.they.are.not"))
          .append(BR)
          .append(joinRootsForPresentation(invalidRoots));
      }
      description.append(BR);
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

  @VisibleForTesting
  @NlsContexts.NotificationContent
  @NotNull
  String getInvalidRootDescriptionItem(@NotNull VcsRootError rootError, @NotNull String vcsName) {
    return VcsBundle.message("roots.notification.content.directory.registered.as.root.but.no.repositories.were.found.there",
                             ROOT_TO_PRESENTABLE.fun(rootError), vcsName);
  }

  @NotNull
  private String joinRootsForPresentation(@NotNull Collection<? extends VcsRootError> errors) {
    List<? extends VcsRootError> sortedRoots = sorted(errors, (root1, root2) -> {
      if (root1.getMapping().isDefaultMapping()) return -1;
      if (root2.getMapping().isDefaultMapping()) return 1;
      return root1.getMapping().getDirectory().compareTo(root2.getMapping().getDirectory());
    });
    return StringUtil.join(sortedRoots, ROOT_TO_PRESENTABLE, BR);
  }

  @NotNull
  private static @NlsContexts.NotificationTitle String makeTitle(@NotNull Collection<? extends VcsRootError> unregisteredRoots,
                                                                 @NotNull Collection<? extends VcsRootError> invalidRoots,
                                                                 boolean rootsAlreadyAdded) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = VcsBundle.message("roots.notification.title.invalid.vcs.root.choice.mapping.mappings", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      String vcs = getVcsName(unregisteredRoots);
      title = rootsAlreadyAdded ? VcsBundle.message("roots.notification.title.vcs.name.integration.enabled", vcs)
                                : VcsBundle.message("notification.title.vcs.name.repository.repositories.found", vcs,
                                                    unregisteredRoots.size());
    }
    else {
      title = VcsBundle.message("roots.notification.title.vcs.root.configuration.problems");
    }
    return title;
  }

  private static String getVcsName(Collection<? extends VcsRootError> roots) {
    String result = null;
    for (VcsRootError root : roots) {
      String vcsName = root.getMapping().getVcs();
      if (result == null) {
        result = vcsName;
      }
      else if (!result.equals(vcsName)) {
        return VcsBundle.message("vcs.generic.name");
      }
    }
    return result;
  }

  @NotNull
  private List<VcsRootError> getImportantUnregisteredMappings(@NotNull Collection<? extends VcsRootError> errors) {
    return filter(errors, error -> {
      VcsDirectoryMapping mapping = error.getMapping();
      return error.getType() == UNREGISTERED_ROOT &&
             !isIgnoredOrExcludedPath(mapping) &&
             !isExplicitlyIgnoredPath(mapping) &&
             !conflictsWithExistingMapping(mapping);
    });
  }

  private boolean areThereExplicitlyIgnoredRoots(Collection<? extends VcsRootError> allErrors) {
    return exists(allErrors, it -> it.getType() == UNREGISTERED_ROOT && isExplicitlyIgnoredPath(it.getMapping()));
  }

  @NotNull
  private static Collection<VcsRootError> getInvalidRoots(@NotNull Collection<? extends VcsRootError> errors) {
    return filter(errors, error -> error.getType() == VcsRootError.Type.EXTRA_MAPPING);
  }


  @VisibleForTesting
  @NotNull
  String getPresentableMapping(@NotNull VcsDirectoryMapping directoryMapping) {
    if (directoryMapping.isDefaultMapping()) return directoryMapping.toString();

    return getPresentableMapping(directoryMapping.getDirectory());
  }

  @VisibleForTesting
  @NotNull
  String getPresentableMapping(@NotNull String mapping) {
    FilePath filePath = VcsUtil.getFilePath(mapping, true);
    String presentablePath = VcsUtil.getPresentablePath(myProject, filePath, false, false);
    return escapeXmlEntities(presentablePath);
  }
}
