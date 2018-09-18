// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * to be called after checkout - notifiers extenders on checkout completion
 */
public class CompositeCheckoutListener implements CheckoutProvider.Listener {
  private final Project myProject;
  private boolean myFoundProject = false;
  private File myFirstDirectory;
  private VcsKey myVcsKey;

  public CompositeCheckoutListener(final Project project) {
    myProject = project;
  }

  @Override
  public void directoryCheckedOut(final File directory, VcsKey vcs) {
    myVcsKey = vcs;
    if (!myFoundProject && directory.isDirectory()) {
      if (myFirstDirectory == null) {
        myFirstDirectory = directory;
      }
      notifyCheckoutListeners(directory, false);
    }
  }

  private void notifyCheckoutListeners(final File directory, boolean checkoutCompleted) {
    ExtensionPointName<CheckoutListener> epName = checkoutCompleted ? CheckoutListener.COMPLETED_EP_NAME : CheckoutListener.EP_NAME;

    CheckoutListener[] listeners = Extensions.getExtensions(epName);
    for (CheckoutListener listener: listeners) {
      myFoundProject = listener.processCheckedOutDirectory(myProject, directory);
      if (myFoundProject) break;
    }

    if (!checkoutCompleted) {
      final VcsAwareCheckoutListener[] vcsAwareExtensions = Extensions.getExtensions(VcsAwareCheckoutListener.EP_NAME);
      for (VcsAwareCheckoutListener extension : vcsAwareExtensions) {
        boolean processingCompleted = extension.processCheckedOutDirectory(myProject, directory, myVcsKey);
        if (processingCompleted) break;
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

  @Nullable
  static Project findProjectByBaseDirLocation(@NotNull final File directory) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), project -> {
      VirtualFile baseDir = project.getBaseDir();
      return baseDir != null && FileUtil.filesEqual(VfsUtilCore.virtualToIoFile(baseDir), directory);
    });
  }
}
