// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public final class HgTestUtil {
  public static void updateDirectoryMappings(Project project, VirtualFile mapRoot) {
    if (project != null && (!project.isDefault()) && project.getBaseDir() != null &&
        VfsUtilCore.isAncestor(project.getBaseDir(), mapRoot, false)) {
      mapRoot.refresh(false, false);
      final String path = mapRoot.equals(project.getBaseDir()) ? "" : mapRoot.getPath();
      ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
      manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), path, HgVcs.VCS_NAME));
    }
  }

  /**
   * Writes the given content to the file.
   *
   * @param file    file which content will be substituted by the given one.
   * @param content new file content
   */
  public static void printToFile(@NotNull VirtualFile file, String content) throws FileNotFoundException {
    try (PrintStream centralPrinter = new PrintStream(new FileOutputStream(file.getPath()))) {
      centralPrinter.print(content);
    }
  }
}
