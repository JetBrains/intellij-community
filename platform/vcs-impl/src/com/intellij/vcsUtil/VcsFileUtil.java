/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsFileUtil {
  /**
   * If multiple paths are specified on the command line, this limit is used to split paths into chunks.
   * The limit is less than OS limit to leave space to quoting, spaces, charset conversion, and commands arguments.
   */
  public static final int FILE_PATH_LIMIT = 7600;

  /**
   * Execute function for each chunk of arguments. Check for being cancelled in process.
   *
   * @param arguments the arguments to chunk
   * @param processor function to execute on each chunk
   * @param <T>       type of result value
   * @return list of result values
   * @throws VcsException
   */
  @NotNull
  public static <T> List<T> foreachChunk(@NotNull List<String> arguments,
                                         @NotNull ThrowableNotNullFunction<List<String>, List<? extends T>, VcsException> processor)
    throws VcsException {
    List<T> result = ContainerUtil.newArrayList();
    List<List<String>> chunks = chunkArguments(arguments);

    for (List<String> chunk : chunks) {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) indicator.checkCanceled();

      result.addAll(processor.fun(chunk));
    }

    return result;
  }

  /**
   * Chunk arguments on the command line
   *
   * @param arguments the arguments to chunk
   * @return a list of lists of arguments
   */
  @NotNull
  public static List<List<String>> chunkArguments(@NotNull List<String> arguments) {
    ArrayList<List<String>> rc = new ArrayList<>();
    int start = 0;
    int size = 0;
    int i = 0;
    for (; i < arguments.size(); i++) {
      String p = arguments.get(i);
      if (size + p.length() > FILE_PATH_LIMIT) {
        if (start == i) {
          rc.add(arguments.subList(i, i + 1));
          start = i + 1;
        }
        else {
          rc.add(arguments.subList(start, i));
          start = i;
        }
        size = 0;
      }
      else {
        size += p.length();
      }
    }
    if (start != arguments.size()) {
      rc.add(arguments.subList(start, i));
    }
    return rc;
  }

  /**
   * The chunk paths
   *
   * @param root  the vcs root
   * @param files the file list
   * @return chunked relative paths
   */
  public static List<List<String>> chunkPaths(VirtualFile root, Collection<FilePath> files) {
    return chunkArguments(toRelativePaths(root, files));
  }

  /**
   * The chunk paths
   *
   * @param root  the vcs root
   * @param files the file list
   * @return chunked relative paths
   */
  public static List<List<String>> chunkFiles(@NotNull VirtualFile root, @NotNull Collection<VirtualFile> files) {
    return chunkArguments(toRelativeFiles(root, files));
  }

  public static String getRelativeFilePath(VirtualFile file, @NotNull final VirtualFile baseDir) {
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
   * Check if character is octal digit
   *
   * @param ch a character to test
   * @return true if the octal digit, false otherwise
   */
  public static boolean isOctal(char ch) {
    return '0' <= ch && ch <= '7';
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
   * @param root a root file
   * @param file a virtual file
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  public static String relativeOrFullPath(final VirtualFile root, VirtualFile file) {
    if (root == null) {
      file.getPath();
    }
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
   * Covert list of files to relative paths
   *
   * @param root      a vcs root
   * @param filePaths a parameters to convert
   * @return a list of relative paths
   * @throws IllegalArgumentException if some path is not under root.
   */
  public static List<String> toRelativePaths(@NotNull VirtualFile root, @NotNull final Collection<FilePath> filePaths) {
    ArrayList<String> rc = new ArrayList<>(filePaths.size());
    for (FilePath path : filePaths) {
      rc.add(relativePath(root, path));
    }
    return rc;
  }

  /**
   * Covert list of files to relative paths
   *
   * @param root  a vcs root
   * @param files a parameters to convert
   * @return a list of relative paths
   * @throws IllegalArgumentException if some path is not under root.
   */
  public static List<String> toRelativeFiles(@NotNull VirtualFile root, @NotNull final Collection<VirtualFile> files) {
    ArrayList<String> rc = new ArrayList<>(files.size());
    for (VirtualFile file : files) {
      rc.add(relativePath(root, file));
    }
    return rc;
  }

  public static void markFilesDirty(@NotNull Project project, @NotNull Collection<VirtualFile> affectedFiles) {
    final VcsDirtyScopeManager dirty = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile file : affectedFiles) {
      if (file.isDirectory()) {
        dirty.dirDirtyRecursively(file);
      }
      else {
        dirty.fileDirty(file);
      }
    }
  }

  public static void markFilesDirty(@NotNull Project project, @NotNull List<FilePath> affectedFiles) {
    final VcsDirtyScopeManager dirty = VcsDirtyScopeManager.getInstance(project);
    for (FilePath file : affectedFiles) {
      if (file.isDirectory()) {
        dirty.dirDirtyRecursively(file);
      }
      else {
        dirty.fileDirty(file);
      }
    }
  }

  /**
   * The get the possible base for the path. It tries to find the parent for the provided path.
   *
   * @param file the file to get base for
   * @param path the path to to check
   * @return the file base
   */
  @Nullable
  public static VirtualFile getPossibleBase(final VirtualFile file, final String... path) {
    if (file == null || path.length == 0) return null;

    VirtualFile current = file;
    final List<VirtualFile> backTrace = new ArrayList<>();
    int idx = path.length - 1;
    while (current != null) {
      if (SystemInfo.isFileSystemCaseSensitive ? current.getName().equals(path[idx]) : current.getName().equalsIgnoreCase(path[idx])) {
        if (idx == 0) {
          return current;
        }
        -- idx;
      } else if (idx != path.length - 1) {
        int diff = path.length - 1 - idx - 1;
        for (int i = 0; i < diff; i++) {
          current = backTrace.remove(backTrace.size() - 1);
        }
        idx = path.length - 1;
        continue;
      }
      backTrace.add(current);
      current = current.getParent();
    }

    return null;
  }
}
