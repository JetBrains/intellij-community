// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * to be called after checkout - notifiers extenders on checkout completion
 */
public final class CompositeCheckoutListener implements CheckoutProvider.Listener {
  private final Project myProject;
  private boolean myFoundProject = false;
  private Path myFirstDirectory;
  private VcsKey myVcsKey;

  public CompositeCheckoutListener(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void directoryCheckedOut(@NotNull File directory, VcsKey vcs) {
    myVcsKey = vcs;
    if (!myFoundProject && directory.isDirectory()) {
      if (myFirstDirectory == null) {
        myFirstDirectory = directory.toPath();
      }
      notifyCheckoutListeners(directory.toPath(), false);
    }
  }

  private void notifyCheckoutListeners(@NotNull Path directory, boolean checkoutCompleted) {
    ExtensionPointName<CheckoutListener> epName = checkoutCompleted ? CheckoutListener.COMPLETED_EP_NAME : CheckoutListener.EP_NAME;

    List<CheckoutListener> listeners = epName.getExtensionList();
    for (CheckoutListener listener: listeners) {
      myFoundProject = listener.processCheckedOutDirectory(myProject, directory);
      if (myFoundProject) {
        break;
      }
    }

    if (!checkoutCompleted) {
      for (VcsAwareCheckoutListener extension : VcsAwareCheckoutListener.EP_NAME.getExtensionList()) {
        boolean processingCompleted = extension.processCheckedOutDirectory(myProject, directory, myVcsKey);
        if (processingCompleted) {
          break;
        }
      }
    }

    Project project = findProjectByBaseDirLocation(directory);
    if (project != null) {
      for (CheckoutListener listener: listeners) {
        listener.processOpenedProject(project);
      }
    }
  }

  @Override
  public void checkoutCompleted() {
    if (!myFoundProject && myFirstDirectory != null) {
      notifyCheckoutListeners(myFirstDirectory, true);
    }
  }

  static @Nullable Project findProjectByBaseDirLocation(@NotNull Path directory) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), project -> {
      String baseDir = project.getBasePath();
      return baseDir != null && FileUtil.pathsEqual(baseDir, directory.toString());
    });
  }
}
