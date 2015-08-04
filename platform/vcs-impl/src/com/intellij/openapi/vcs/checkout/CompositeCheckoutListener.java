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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
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

  public void checkoutCompleted() {
    if (!myFoundProject && myFirstDirectory != null) {
      notifyCheckoutListeners(myFirstDirectory, true);
    }
  }

  @Nullable
  static Project findProjectByBaseDirLocation(@NotNull final File directory) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), new Condition<Project>() {
      @Override
      public boolean value(Project project) {
        VirtualFile baseDir = project.getBaseDir();
        return baseDir != null && FileUtil.filesEqual(VfsUtilCore.virtualToIoFile(baseDir), directory);
      }
    });
  }
}
