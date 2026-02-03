// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

/**
 * to be called after checkout - notifiers extenders on checkout completion
 */
@ApiStatus.Internal
public final class CompositeCheckoutListener implements CheckoutProvider.Listener {
  private static final Logger LOG = Logger.getInstance(CompositeCheckoutListener.class);

  private final Project myProject;

  private boolean myFoundProject = false;
  private Path myFirstDirectory;

  public CompositeCheckoutListener(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void directoryCheckedOut(@NotNull File file, VcsKey vcs) {
    if (myFoundProject) return;
    if (!file.isDirectory()) return;

    Path directory = file.toPath();
    if (myFirstDirectory == null) myFirstDirectory = directory;

    for (CheckoutListener listener : CheckoutListener.EP_NAME.getExtensionList()) {
      try {
        myFoundProject = listener.processCheckedOutDirectory(myProject, directory);
        if (myFoundProject) {
          LOG.debug(String.format("Cloned dir '%s' processed by %s", directory, listener));
          break;
        }
      }
      catch (ProcessCanceledException pce) {
        LOG.info("CompositeCheckoutListener.directoryCheckedOut call has been canceled by: " + pce);
        break;
      }
      catch (Exception e) {
        LOG.warn("Error in checkout listener: " + listener, e);
      }
    }

    for (VcsAwareCheckoutListener listener : VcsAwareCheckoutListener.EP_NAME.getExtensionList()) {
      try {
        boolean processingCompleted = listener.processCheckedOutDirectory(myProject, directory, vcs);
        if (processingCompleted) {
          LOG.debug(String.format("Cloned dir '%s' processed by %s", directory, listener));
          break;
        }
      }
      catch (Exception e) {
        LOG.warn("Error in checkout listener: " + listener, e);
      }
    }

    Project project = findProjectByBaseDirLocation(directory);
    if (project != null) {
      VcsStatisticsCollector.CLONED_PROJECT_OPENED.log(project);
    }
  }

  @Override
  public void checkoutCompleted() {
    if (myFoundProject) return;

    Path directory = myFirstDirectory;
    if (directory == null) return;

    for (CheckoutListener listener : CheckoutListener.COMPLETED_EP_NAME.getExtensionList()) {
      try {
        boolean foundProject = listener.processCheckedOutDirectory(myProject, directory);
        if (foundProject) {
          LOG.debug(String.format("Cloned dir '%s' processed by %s", directory, listener));
          break;
        }
      }
      catch (Exception e) {
        LOG.warn("Error in checkout listener: " + listener, e);
      }
    }

    Project project = findProjectByBaseDirLocation(directory);
    if (project != null) {
      VcsStatisticsCollector.CLONED_PROJECT_OPENED.log(project);
    }
  }

  static @Nullable Project findProjectByBaseDirLocation(@NotNull Path directory) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), project -> {
      String baseDir = project.getBasePath();
      return baseDir != null && FileUtil.pathsEqual(baseDir, directory.toString());
    });
  }
}
