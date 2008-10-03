package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * 
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Git utility/helper methods
 */
public class GitUtil {

  /**
   * A private constructor to suppress instance creation
   */
  private GitUtil() {
    // do nothink
  }

  @NotNull
  public static VirtualFile getVcsRoot(@NotNull final Project project, @NotNull final FilePath filePath) {
    VirtualFile vfile = VcsUtil.getVcsRootFor(project, filePath);
    if (vfile == null) vfile = GitFileSystem.getInstance().findFileByPath(project, filePath.getPath());

    return vfile;
  }

  @NotNull
  public static VirtualFile getVcsRoot(@NotNull final Project project, final VirtualFile virtualFile) {
    VirtualFile vfile = VcsUtil.getVcsRootFor(project, virtualFile);
    if (vfile == null) vfile = project.getBaseDir();
    return vfile;
  }

  @NotNull
  private static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(@NotNull Project project, @NotNull List<VirtualFile> virtualFiles) {
    Map<VirtualFile, List<VirtualFile>> result = new HashMap<VirtualFile, List<VirtualFile>>();

    for (VirtualFile file : virtualFiles) {
      final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
      assert vcsRoot != null;

      List<VirtualFile> files = result.get(vcsRoot);
      if (files == null) {
        files = new ArrayList<VirtualFile>();
        result.put(vcsRoot, files);
      }
      files.add(file);
    }

    return result;
  }

  @NotNull
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(Project project, VirtualFile[] affectedFiles) {
    return sortFilesByVcsRoot(project, Arrays.asList(affectedFiles));
  }

  public static String getRelativeFilePath(VirtualFile file, @NotNull final VirtualFile baseDir) {
    return getRelativeFilePath(file.getPath(), baseDir);
  }

  public static String getRelativeFilePath(FilePath file, @NotNull final VirtualFile baseDir) {
    return getRelativeFilePath(file.getPath(), baseDir);
  }

  public static String getRelativeFilePath(String file, @NotNull final VirtualFile baseDir) {
    if (SystemInfo.isWindows) {
      file = file.replace('\\', '/');
    }
    final String basePath = baseDir.getPath();
    if (!file.startsWith(basePath)) {
      return file;
    }
    else if (file.equals(basePath)) return ".";
    return file.substring(baseDir.getPath().length() + 1);
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param ex        the exception
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final VcsException ex, @NonNls @NotNull final String operation) {
    showOperationError(project, operation, ex.getMessage());
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param message   the error description
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final String operation, final String message) {
    Messages.showErrorDialog(project, message, GitBundle.message("error.occurred.during", operation));
  }

  /**
   * @return a temporary directory to use
   */
  @NotNull
  public static VirtualFile getTempDir() throws VcsException {
    try {
      @SuppressWarnings({"HardCodedStringLiteral"}) File temp = File.createTempFile("git-temp-file", "txt");
      try {
        final File parentFile = temp.getParentFile();
        if (parentFile == null) {
          throw new Exception("Missing parent in " + temp);
        }
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(parentFile);
        if (vFile == null) {
          throw new Exception("Missing virtual file for dir " + parentFile);
        }
        return vFile;
      }
      finally {
        //noinspection ResultOfMethodCallIgnored
        temp.delete();
      }
    }
    catch (Exception e) {
      throw new VcsException("Unable to locate temporary directory", e);
    }
  }
}