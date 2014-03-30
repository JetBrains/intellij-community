/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgTestUtil {

  public static void updateDirectoryMappings(Project project, VirtualFile mapRoot) {
    if (project != null && (!project.isDefault()) && project.getBaseDir() != null && VfsUtil
      .isAncestor(project.getBaseDir(), mapRoot, false)) {
      mapRoot.refresh(false, false);
      final String path = mapRoot.equals(project.getBaseDir()) ? "" : mapRoot.getPath();
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      final List<VcsDirectoryMapping> vcsDirectoryMappings = new ArrayList<VcsDirectoryMapping>(vcsManager.getDirectoryMappings());
      VcsDirectoryMapping mapping = new VcsDirectoryMapping(path, HgVcs.VCS_NAME);
      for (int i = 0; i < vcsDirectoryMappings.size(); i++) {
        final VcsDirectoryMapping m = vcsDirectoryMappings.get(i);
        if (m.getDirectory().equals(path)) {
          if (m.getVcs().length() == 0) {
            vcsDirectoryMappings.set(i, mapping);
            mapping = null;
            break;
          }
          else if (m.getVcs().equals(mapping.getVcs())) {
            mapping = null;
            break;
          }
        }
      }
      if (mapping != null) {
        vcsDirectoryMappings.add(mapping);
      }
      vcsManager.setDirectoryMappings(vcsDirectoryMappings);
      vcsManager.updateActiveVcss();
    }
  }

  /**
   * Writes the given content to the file.
   *
   * @param file    file which content will be substituted by the given one.
   * @param content new file content
   */
  public static void printToFile(@NotNull VirtualFile file, String content) throws FileNotFoundException {
    PrintStream centralPrinter = null;
    try {
      centralPrinter = new PrintStream(new FileOutputStream(new File(file.getPath())));
      centralPrinter.print(content);
      centralPrinter.close();
    }
    finally {
      if (centralPrinter != null) {
        centralPrinter.close();
      }
    }
  }
}
