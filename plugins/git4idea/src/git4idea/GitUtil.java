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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.config.GitConfigUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Git utility/helper methods
 */
public class GitUtil {

  /**
   * A private constructor to suppress instance creation
   */
  private GitUtil() {
    // do nothing
  }

  /**
   * Sort files by Git root
   *
   * @param virtualFiles files to sort
   * @return sorted files
   * @throws VcsException if non git files are passed
   */
  @NotNull
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Collection<VirtualFile> virtualFiles) throws VcsException {
    return sortFilesByGitRoot(virtualFiles, false);
  }

  /**
   * Sort files by Git root
   *
   * @param virtualFiles files to sort
   * @param ignoreNonGit if true, non-git files are ignored
   * @return sorted files
   * @throws VcsException if non git files are passed when {@code ignoreNonGit} is false
   */
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(Collection<VirtualFile> virtualFiles, boolean ignoreNonGit)
    throws VcsException {
    Map<VirtualFile, List<VirtualFile>> result = new HashMap<VirtualFile, List<VirtualFile>>();
    for (VirtualFile file : virtualFiles) {
      final VirtualFile vcsRoot = gitRootOrNull(file);
      if (vcsRoot == null) {
        if (ignoreNonGit) {
          continue;
        }
        else {
          throw new VcsException("The file " + file.getPath() + " is not under Git");
        }
      }
      List<VirtualFile> files = result.get(vcsRoot);
      if (files == null) {
        files = new ArrayList<VirtualFile>();
        result.put(vcsRoot, files);
      }
      files.add(file);
    }
    return result;
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
   * Sort files by vcs root
   *
   * @param files files to sort.
   * @return the map from root to the files under the root
   * @throws VcsException if non git files are passed
   */
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(final Collection<FilePath> files) throws VcsException {
    return sortFilePathsByGitRoot(files, false);
  }

  /**
   * Sort files by vcs root
   *
   * @param files        files to sort.
   * @param ignoreNonGit if true, non-git files are ignored
   * @return the map from root to the files under the root
   * @throws VcsException if non git files are passed when {@code ignoreNonGit} is false
   */
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(Collection<FilePath> files, boolean ignoreNonGit)
    throws VcsException {
    Map<VirtualFile, List<FilePath>> rc = new HashMap<VirtualFile, List<FilePath>>();
    for (FilePath p : files) {
      VirtualFile root = getGitRootOrNull(p);
      if (root == null) {
        if (ignoreNonGit) {
          continue;
        }
        else {
          throw new VcsException("The file " + p.getPath() + " is not under Git");
        }
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
   * @throws VcsException if the path in invalid
   */
  public static String unescapePath(String path) throws VcsException {
    final int l = path.length();
    StringBuilder rc = new StringBuilder(l);
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\\') {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i >= l) {
          throw new VcsException("Unterminated escape sequence in the path: " + path);
        }
        final char e = path.charAt(i);
        switch (e) {
          case '\\':
            rc.append('\\');
            break;
          case 't':
            rc.append('\t');
            break;
          case 'n':
            rc.append('\n');
            break;
          default:
            if (isOctal(e)) {
              // collect sequence of characters as a byte array.
              // count bytes first
              int n = 0;
              for (int j = i; j < l;) {
                if (isOctal(path.charAt(j))) {
                  n++;
                  for (int k = 0; k < 3 && j < l && isOctal(path.charAt(j)); k++) {
                    //noinspection AssignmentToForLoopParameter
                    j++;
                  }
                }
                if (j + 1 >= l || path.charAt(j) != '\\' || !isOctal(path.charAt(j + 1))) {
                  break;
                }
                //noinspection AssignmentToForLoopParameter
                j++;
              }
              // convert to byte array
              byte[] b = new byte[n];
              n = 0;
              while (i < l) {
                if (isOctal(path.charAt(i))) {
                  int code = 0;
                  for (int k = 0; k < 3 && i < l && isOctal(path.charAt(i)); k++) {
                    code = code * 8 + (path.charAt(i) - '0');
                    //noinspection AssignmentToForLoopParameter
                    i++;
                  }
                  b[n++] = (byte)code;
                }
                if (i + 1 >= l || path.charAt(i) != '\\' || !isOctal(path.charAt(i + 1))) {
                  break;
                }
                //noinspection AssignmentToForLoopParameter
                i++;
              }
              assert n == b.length;
              // add them to string
              final String encoding = GitConfigUtil.getFileNameEncoding();
              try {
                rc.append(new String(b, encoding));
              }
              catch (UnsupportedEncodingException e1) {
                throw new IllegalStateException("The file name encoding is unsuported: " + encoding);
              }
            }
            else {
              throw new VcsException("Unknown escape sequence '\\" + path.charAt(i) + "' in the path: " + path);
            }
        }
      }
      else {
        rc.append(c);
      }
    }
    return rc.toString();
  }

  /**
   * Check if character is octal digit
   *
   * @param ch a character to test
   * @return true if the octal digit, false otherwise
   */
  private static boolean isOctal(char ch) {
    return '0' <= ch && ch <= '7';
  }

  /**
   * Parse UNIX timestamp as it is returned by the git
   *
   * @param value a value to parse
   * @return timestamp as {@link Date} object
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
  public static Set<VirtualFile> gitRootsForPaths(final Collection<VirtualFile> roots) {
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
   * @param filePath a file path
   * @return git root for the file
   * @throws IllegalArgumentException if the file is not under git
   * @throws VcsException             if the file is not under git
   */
  public static VirtualFile getGitRoot(final FilePath filePath) throws VcsException {
    VirtualFile root = getGitRootOrNull(filePath);
    if (root != null) {
      return root;
    }
    throw new VcsException("The file " + filePath + " is not under git.");
  }

  /**
   * Return a git root for the file path (the parent directory with ".git" subdirectory)
   *
   * @param filePath a file path
   * @return git root for the file or null if the file is not under git
   */
  @Nullable
  public static VirtualFile getGitRootOrNull(final FilePath filePath) {
    File file = filePath.getIOFile();
    while (file != null && (!file.exists() || !file.isDirectory() || !new File(file, ".git").exists())) {
      file = file.getParentFile();
    }
    if (file == null) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  /**
   * Return a git root for the file (the parent directory with ".git" subdirectory)
   *
   * @param file the file to check
   * @return git root for the file
   * @throws VcsException if the file is not under git
   */
  public static VirtualFile getGitRoot(@NotNull final VirtualFile file) throws VcsException {
    final VirtualFile root = gitRootOrNull(file);
    if (root != null) {
      return root;
    }
    else {
      throw new VcsException("The file " + file.getPath() + " is not under git.");
    }
  }

  /**
   * Return a git root for the file (the parent directory with ".git" subdirectory)
   *
   * @param file the file to check
   * @return git root for the file or null if the file is not not under Git
   */
  @Nullable
  public static VirtualFile gitRootOrNull(final VirtualFile file) {
    if (file instanceof AbstractVcsVirtualFile) {
      return getGitRootOrNull(VcsUtil.getFilePath(file.getPath()));
    }
    VirtualFile root = file;
    while (root != null) {
      if (root.findFileByRelativePath(".git") != null) {
        return root;
      }
      root = root.getParent();
    }
    return root;
  }


  /**
   * Check if the virtual file under git
   *
   * @param vFile a virtual file
   * @return true if the file is under git
   */
  public static boolean isUnderGit(final VirtualFile vFile) {
    return gitRootOrNull(vFile) != null;
  }

  /**
   * Get relative path
   *
   * @param root a root path
   * @param path a path to file (possibly deleted file)
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativePath(final VirtualFile root, FilePath path) {
    return relativePath(VfsUtil.virtualToIoFile(root), path.getIOFile());
  }


  /**
   * Get relative path
   *
   * @param root a root path
   * @param path a path to file (possibly deleted file)
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativePath(final File root, FilePath path) {
    return relativePath(root, path.getIOFile());
  }

  /**
   * Get relative path
   *
   * @param root a root path
   * @param file a virtual file
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativePath(final File root, VirtualFile file) {
    return relativePath(root, VfsUtil.virtualToIoFile(file));
  }

  /**
   * Get relative path
   *
   * @param root a root file
   * @param file a virtual file
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativePath(final VirtualFile root, VirtualFile file) {
    return relativePath(VfsUtil.virtualToIoFile(root), VfsUtil.virtualToIoFile(file));
  }

  /**
   * Get relative path
   *
   * @param root a root path
   * @param path a path to file (possibly deleted file)
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativePath(final File root, File path) {
    String rc = FileUtil.getRelativePath(root, path);
    if (rc == null) {
      throw new IllegalArgumentException("The file " + path + " cannot be made relative to " + root);
    }
    return rc.replace(File.separatorChar, '/');
  }

  /**
   * Refresh files
   *
   * @param project       a project
   * @param affectedFiles affected files and directories
   */
  public static void refreshFiles(@NotNull final Project project, @NotNull final Collection<VirtualFile> affectedFiles) {
    final VcsDirtyScopeManager dirty = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile file : affectedFiles) {
      if (!file.isValid()) {
        continue;
      }
      file.refresh(false, true);
      if (file.isDirectory()) {
        dirty.dirDirtyRecursively(file);
      }
      else {
        dirty.fileDirty(file);
      }
    }
  }


  /**
   * Refresh files
   *
   * @param project       a project
   * @param affectedFiles affected files and directories
   */
  public static void refreshFiles(Project project, List<FilePath> affectedFiles) {
    final VcsDirtyScopeManager dirty = VcsDirtyScopeManager.getInstance(project);
    for (FilePath file : affectedFiles) {
      VirtualFile vFile = VcsUtil.getVirtualFile(file.getIOFile());
      if (vFile != null) {
        vFile.refresh(false, true);
      }
      if (file.isDirectory()) {
        dirty.dirDirtyRecursively(file);
      }
      else {
        dirty.fileDirty(file);
      }
    }
  }

  /**
   * Return committer name based on author name and committer name
   *
   * @param authorName    the name of author
   * @param committerName the name of committer
   * @return just a name if they are equal, or name that includes both author and committer
   */
  public static String adjustAuthorName(final String authorName, String committerName) {
    if (!authorName.equals(committerName)) {
      //noinspection HardCodedStringLiteral
      committerName = authorName + ", via " + committerName;
    }
    return committerName;
  }

  /**
   * Check if the file path is under git
   *
   * @param path the path
   * @return true if the file path is under git
   */
  public static boolean isUnderGit(final FilePath path) {
    return getGitRootOrNull(path) != null;
  }

  /**
   * Get git roots for the selected paths
   *
   * @param filePaths the context paths
   * @return a set of git roots
   */
  public static Set<VirtualFile> gitRoots(final Collection<FilePath> filePaths) {
    HashSet<VirtualFile> rc = new HashSet<VirtualFile>();
    for (FilePath path : filePaths) {
      final VirtualFile root = getGitRootOrNull(path);
      if (root != null) {
        rc.add(root);
      }
    }
    return rc;
  }

  /**
   * Get git time (UNIX time) basing on the date object
   *
   * @param time the time to convert
   * @return the time in git format
   */
  public static String gitTime(Date time) {
    long t = time.getTime() / 1000;
    return Long.toString(t);
  }

  /**
   * Format revision number from long to 16-digit abbreviated revision
   *
   * @param rev the abbreviated revision number as long
   * @return the revision string
   */
  public static String formatLongRev(long rev) {
    return String.format("%015x%x", (rev >>> 4), rev & 0xF);
  }
}