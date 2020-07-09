// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.impl.VcsEP;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.openapi.vfs.VirtualFileVisitor.*;

public final class VcsRootScanner implements AsyncVfsEventsListener {
  private static final Logger LOG = Logger.getInstance(VcsRootScanner.class);

  @NotNull private final VcsRootProblemNotifier myRootProblemNotifier;
  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;

  @NotNull private final Alarm myAlarm;
  private static final long WAIT_BEFORE_SCAN = TimeUnit.SECONDS.toMillis(1);

  public static void start(@NotNull Project project) {
    new VcsRootScanner(project).scheduleScan();
  }

  private VcsRootScanner(@NotNull Project project) {
    myProject = project;
    myProjectManager = ProjectRootManager.getInstance(project);
    myRootProblemNotifier = VcsRootProblemNotifier.getInstance(project);

    AsyncVfsEventsPostProcessor.getInstance().addListener(this, project);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

    VcsRootChecker.EXTENSION_POINT_NAME.addChangeListener(() -> scheduleScan(), project);
    VcsEP.EP_NAME.addChangeListener(() -> scheduleScan(), project);
  }

  @Override
  public void filesChanged(@NotNull List<? extends VFileEvent> events) {
    List<VcsRootChecker> checkers = VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList();
    if (checkers.isEmpty()) return;

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file != null && file.isDirectory()) {
        visitDirsRecursivelyWithoutExcluded(myProject, myProjectManager, file, dir -> {
          if (isVcsDir(checkers, dir.getName())) {
            scheduleScan();
            return skipTo(file);
          }
          return CONTINUE;
        });
      }
    }
  }

  static void visitDirsRecursivelyWithoutExcluded(@NotNull Project project,
                                                  @NotNull ProjectRootManager projectRootManager,
                                                  @NotNull VirtualFile root,
                                                  @NotNull Function<? super VirtualFile, Result> dirFound) {
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    Option depthLimit = limit(Registry.intValue("vcs.root.detector.folder.depth"));
    Pattern ignorePattern = parseDirIgnorePattern();

    if (isUnderIgnoredDirectory(project, ignorePattern, root)) return;

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>(NO_FOLLOW_SYMLINKS, depthLimit) {
      @NotNull
      @Override
      public VirtualFileVisitor.Result visitFileEx(@NotNull VirtualFile file) {
        if (!file.isDirectory()) {
          return CONTINUE;
        }

        if (isIgnoredDirectory(project, ignorePattern, file)) {
          return SKIP_CHILDREN;
        }

        if (ReadAction.compute(() -> project.isDisposed() || !fileIndex.isInContent(file))) {
          return SKIP_CHILDREN;
        }

        return dirFound.apply(file);
      }
    });
  }

  private static boolean isVcsDir(@NotNull List<VcsRootChecker> checkers, @NotNull String filePath) {
    return checkers.stream().anyMatch(it -> it.isVcsDir(filePath));
  }

  private void scheduleScan() {
    if (myAlarm.isDisposed()) return;
    if (VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList().isEmpty()) return;

    myAlarm.cancelAllRequests(); // one scan is enough, no need to queue, they all do the same
    myAlarm.addRequest(() -> BackgroundTaskUtil.runUnderDisposeAwareIndicator(myAlarm, () ->
      myRootProblemNotifier.rescanAndNotifyIfNeeded()), WAIT_BEFORE_SCAN);
  }


  static boolean isUnderIgnoredDirectory(@NotNull Project project, @Nullable Pattern ignorePattern, @NotNull VirtualFile dir) {
    VirtualFile parent = dir;
    while (parent != null) {
      if (isIgnoredDirectory(project, ignorePattern, parent)) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean isIgnoredDirectory(@NotNull Project project, @Nullable Pattern ignorePattern, @NotNull VirtualFile dir) {
    if (ProjectLevelVcsManager.getInstance(project).isIgnored(dir)) {
      LOG.debug("Skipping ignored dir: ", dir);
      return true;
    }
    if (ignorePattern != null && ignorePattern.matcher(dir.getName()).matches()) {
      LOG.debug("Skipping dir by pattern: ", dir);
      return true;
    }
    return false;
  }

  @Nullable
  static Pattern parseDirIgnorePattern() {
    try {
      return Pattern.compile(Registry.stringValue("vcs.root.detector.ignore.pattern"));
    }
    catch (MissingResourceException | PatternSyntaxException e) {
      LOG.warn(e);
      return null;
    }
  }
}
