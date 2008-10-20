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
import git4idea.i18n.GitBundle;
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

  /**
   * Get {@link java.io.File} from {@link VirtualFile}. Note that only files from {@link LocalFileSystem} are supported. Since git cannot work with other kinds of filesystems anyway.
   *
   * @param file a virtual file
   * @return a matching {@link java.io.File}
   */
  public static File getIOFile(VirtualFile file) {
    if (file.getFileSystem() instanceof LocalFileSystem) {
      return new File(file.getPath());
    }
    else {
      throw new IllegalArgumentException("Only local file system is supported: " + file.getFileSystem().getClass().getName());
    }
  }

  @NotNull
  public static VirtualFile getVcsRoot(@NotNull final Project project, @NotNull final FilePath filePath) {
    VirtualFile vfile = VcsUtil.getVcsRootFor(project, filePath);
    if (vfile == null) {
      throw new IllegalStateException("The file is not under git: " + filePath);
    }
    return vfile;
  }

  @NotNull
  public static VirtualFile getVcsRoot(@NotNull final Project project, final VirtualFile virtualFile) {
    VirtualFile vfile = VcsUtil.getVcsRootFor(project, virtualFile);
    if (vfile == null) vfile = project.getBaseDir();
    return vfile;
  }

  @NotNull
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(@NotNull Project project,
                                                                       @NotNull Collection<VirtualFile> virtualFiles) {
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

  /**
   * Sort files by vcs root
   *
   * @param files files to delete.
   * @return the map from root to the files under the root
   */
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByVcsRoot(Project project, final Collection<FilePath> files) {
    Map<VirtualFile, List<FilePath>> rc = new HashMap<VirtualFile, List<FilePath>>();
    // TODO fix for submodules (several subroots within single vcsroot)
    for (FilePath p : files) {
      VirtualFile root = VcsUtil.getVcsRootFor(project, p);
      if (root == null) {
        // non vcs file
        continue;
      }
      List<FilePath> l = rc.get(root);
      if (l == null) {
        l = new ArrayList<FilePath>();
        rc.put(root, l);
      }
      l.add(p);
    }
    return rc;
  }

  /**
   * Unescape path returned by the Git
   *
   * @param path a path to unescape
   * @return unescaped path
   * @throws com.intellij.openapi.vcs.VcsException
   *          if the path in invalid
   */
  public static String unescapePath(String path) throws VcsException {
    StringBuilder rc = new StringBuilder(path.length());
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\\') {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i <= path.length()) {
          throw new VcsException("Unterminated escape sequence in the path: " + path);
        }
        switch (path.charAt(i)) {
          case '\\':
            rc.append('\\');
            break;
          case 't':
            rc.append('\t');
            break;
          case 'n':
            rc.append('\n');
            break;
          // TODO support unicode
          default:
            throw new VcsException("Unknown escape sequence '\\" + path.charAt(i) + "' in the path: " + path);
        }
      }
      else {
        rc.append(c);
      }
    }
    return rc.toString();
  }

  /**
   * Parse unix timestamp as it is returned by the git
   *
   * @param value a value to parse
   * @return timestamp as {@link java.util.Date} object
   */
  public static Date parseTimestamp(String value) {
    return new Date(Long.parseLong(value.trim()) * 1000);
  }

  /**
   * Get git roots from content roots
   *
   * @param roots git content roots
   * @return a content root
   */
  public static Collection<VirtualFile> gitRoots(final Collection<VirtualFile> roots) {
    HashSet<VirtualFile> rc = new HashSet<VirtualFile>();
    for (VirtualFile root : roots) {
      VirtualFile f = root;
      do {
        if (f.findFileByRelativePath(".git") != null) {
          rc.add(f);
          break;
        }
        f = f.getParent();
      }
      while (f != null);
    }
    return rc;
  }

  /**
   * Return a git root for the file path (the parent directory with ".git" subdirectory)
   *
   * @param project  a project
   * @param filePath a file path
   * @return git root for the file
   * @throws IllegalArgumentException if the file is not under git
   */
  public static VirtualFile getGitRoot(final Project project, final FilePath filePath) {
    VirtualFile root = VcsUtil.getVcsRootFor(project, filePath);
    while (root != null) {
      if (root.findFileByRelativePath(".git") != null) {
        return root;
      }
      root = root.getParent();
    }
    throw new IllegalArgumentException("The file " + filePath + " is not under git.");
  }

  /**
   * Check if the virtual file under git
   *
   * @param project a context project
   * @param vFile   a virtual file
   * @return true if the file is under git
   */
  public static boolean isUnderGit(final Project project, final VirtualFile vFile) {
    VirtualFile root = vFile;
    while (root != null) {
      if (root.findFileByRelativePath(".git") != null) {
        return true;
      }
      root = root.getParent();
    }
    return false;
  }
}