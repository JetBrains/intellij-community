// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * <p>{@link VcsUtil} extension that needs access to the {@code intellij.platform.vcs.impl} module.</p>
 *
 * @author Kirill Likhodedov
 */
public class VcsImplUtil {
  /**
   * Shows error message with specified message text and title.
   * The parent component is the root frame.
   *
   * @param project Current project component
   * @param message information message
   * @param title   Dialog title
   */
  public static void showErrorMessage(final Project project, final String message, final String title)
  {
    Runnable task = () -> Messages.showErrorDialog(project, message, title);
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(task, null, project);
  }

  @NotNull
  public static String getShortVcsRootName(@NotNull Project project, @NotNull VirtualFile root) {
    VirtualFile projectDir = project.getBaseDir();

    String repositoryPath = root.getPresentableUrl();
    if (projectDir != null) {
      String relativePath = VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar);
      if (relativePath != null) {
        repositoryPath = relativePath;
      }
    }

    return repositoryPath.isEmpty() ? root.getName() : repositoryPath;
  }

  public static boolean isNonModalCommit() {
    return Registry.is("vcs.non.modal.commit");
  }
}
