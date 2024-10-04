// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public final class VcsFileUtil {
  /**
   * If multiple paths are specified on the command line, this limit is used to split paths into chunks.
   * The limit is less than OS limit to leave space to quoting, spaces, charset conversion, and commands arguments.
   */
  public static final int FILE_PATH_LIMIT = 7600;

  /**
   * Execute function for each chunk of arguments. Check for being cancelled in process.
   *
   * @param arguments the arguments to chunk
   * @param groupSize size of argument groups that should be put in the same chunk (like a name and a value)
   * @param consumer  consumer to feed each chunk
   */
  public static void foreachChunk(@NotNull List<String> arguments,
                                  int groupSize,
                                  @NotNull ThrowableConsumer<? super List<String>, ? extends VcsException> consumer)
    throws VcsException {
    List<List<String>> chunks = chunkArguments(arguments, groupSize);

    for (List<String> chunk : chunks) {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) indicator.checkCanceled();

      consumer.consume(chunk);
    }
  }

  /**
   * Chunk arguments on the command line
   *
   * @param arguments the arguments to chunk
   * @return a list of lists of arguments
   */
  @NotNull
  public static List<List<String>> chunkArguments(@NotNull List<String> arguments) {
    return chunkArguments(arguments, 1);
  }

  /**
   * Chunk arguments on the command line
   *
   * @param arguments the arguments to chunk, number of arguments should be divisible by groupSize
   * @param groupSize size of argument groups that should be put in the same chunk
   * @return a list of lists of arguments
   */
  @NotNull
  public static List<List<String>> chunkArguments(@NotNull List<String> arguments, int groupSize) {
    assert arguments.size() % groupSize == 0 : "Arguments size should be divisible by group size";

    ArrayList<List<String>> rc = new ArrayList<>();
    int start = 0;
    int size = 0;
    int i = 0;
    for (; i < arguments.size(); i += groupSize) {
      int length = 0;
      for (int j = 0; j < groupSize; j++) {
        length += arguments.get(i + j).length();
      }
      if (size + length > FILE_PATH_LIMIT) {
        if (start == i) {
          // to avoid empty chunks
          rc.add(arguments.subList(i, i + groupSize));
          start = i + groupSize;
          size = 0;
        }
        else {
          rc.add(arguments.subList(start, i));
          start = i;
          size = length;
        }
      }
      else {
        size += length;
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
  public static List<List<String>> chunkPaths(VirtualFile root, Collection<? extends FilePath> files) {
    return chunkArguments(toRelativePaths(root, files));
  }

  /**
   * The chunk paths
   *
   * @param root  the vcs root
   * @param files the file list
   * @return chunked relative paths
   */
  public static List<List<String>> chunkFiles(@NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files) {
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
    return relativePath(VfsUtilCore.virtualToIoFile(root), path.getIOFile());
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
    return relativePath(root, VfsUtilCore.virtualToIoFile(file));
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
    return relativePath(VfsUtilCore.virtualToIoFile(root), VfsUtilCore.virtualToIoFile(file));
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
    return relativePath(VfsUtilCore.virtualToIoFile(root), VfsUtilCore.virtualToIoFile(file));
  }

  /**
   * Get relative path
   *
   * @param root a root path
   * @param file a target path
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  @NotNull
  public static String relativePath(@NotNull FilePath root, @NotNull FilePath file) {
    return relativePath(root.getIOFile(), file.getIOFile());
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
  public static List<String> toRelativePaths(@NotNull VirtualFile root, @NotNull final Collection<? extends FilePath> filePaths) {
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
  public static List<String> toRelativeFiles(@NotNull VirtualFile root, @NotNull final Collection<? extends VirtualFile> files) {
    ArrayList<String> rc = new ArrayList<>(files.size());
    for (VirtualFile file : files) {
      rc.add(relativePath(root, file));
    }
    return rc;
  }

  public static void markFilesDirty(@NotNull Project project, @NotNull Collection<? extends VirtualFile> affectedFiles) {
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

  public static void markFilesDirty(@NotNull Project project, @NotNull List<? extends FilePath> affectedFiles) {
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

  public static void addFilesToVcsWithConfirmation(@NotNull Project project, VirtualFile... virtualFiles) {
    addFilesToVcsWithConfirmation(project, Arrays.asList(virtualFiles));
  }

  /**
   * Finds all VCSs related to the passed files, suggests user to add files to the respected VCSs honoring addition and silence settings
   * and adds them if user or settings confirmed addition. Because of potentially long operation of collecting files it's highly recommended
   * to invoke it on the pooled thread. Method works synchronously.
   *
   * @param project      project we work in
   * @param virtualFiles collection of virtual files to add; directories being added recursively
   */
  public static void addFilesToVcsWithConfirmation(@NotNull Project project,
                                                   @NotNull Collection<? extends VirtualFile> virtualFiles) {
    if (virtualFiles.isEmpty()) {
      return;
    }
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    Multimap<AbstractVcs, VirtualFile> vcsMap = ArrayListMultimap.create();
    for (VirtualFile createdFile : virtualFiles) {
      AbstractVcs vcs = vcsManager.getVcsFor(createdFile);
      if (vcs == null) {
        continue;
      }
      VfsUtil.processFileRecursivelyWithoutIgnored(createdFile, (virtualFile) -> vcsMap.put(vcs, virtualFile));
    }

    for (AbstractVcs vcs : vcsMap.keySet()) {
      VcsShowConfirmationOption addOption =
        vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
      if (addOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;
      List<VirtualFile> filesList = new ArrayList<>(vcsMap.get(vcs));
      if (addOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
        performAdditions(vcs, filesList);
      }
      else {
        AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);

        Ref<Collection<VirtualFile>> filesToAdd = Ref.create();

        ApplicationManager.getApplication().invokeAndWait(() -> filesToAdd.set(
          helper
            .selectFilesToProcess(
              new ArrayList<>(filesList),
              VcsBundle.message("confirmation.title.add.files.to", vcs.getDisplayName()),
              null,
              VcsBundle.message("confirmation.title.add.file.to", vcs.getDisplayName()),
              null,
              addOption))
        );

        if (!filesToAdd.isNull()) {
          performAdditions(vcs, new ArrayList<>(filesToAdd.get()));
        }
      }
    }
  }

  private static void performAdditions(@NotNull AbstractVcs vcs,
                                       @NotNull List<? extends VirtualFile> value) {
    CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
    if (checkinEnvironment != null) {
      checkinEnvironment.scheduleUnversionedFilesForAddition(value);
    }
  }

  /**
   * @see FileUtil#toCanonicalPath
   */
  public static boolean isAncestor(@NotNull @SystemIndependent String ancestor, @NotNull @SystemIndependent String path, boolean strict) {
    return FileUtil.startsWith(path, ancestor, SystemInfo.isFileSystemCaseSensitive, strict);
  }

  public static boolean isAncestor(@NotNull FilePath ancestor, @NotNull FilePath path, boolean strict) {
    return isAncestor(ancestor.getPath(), path.getPath(), strict);
  }

  public static boolean isAncestor(@NotNull VirtualFile root, @NotNull FilePath path) {
    return isAncestor(root.getPath(), path.getPath(), false);
  }

  /**
   * <p>Unescape path returned by Git.</p>
   * <p>
   * If there are quotes in the file name, Git not only escapes them, but also encloses the file name into quotes:
   * {@code "\"quote"}
   * </p>
   * <p>
   * If there are spaces in the file name, Git displays the name as is, without escaping spaces and without enclosing name in quotes.
   * </p>
   *
   * @param path a path to unescape
   * @return unescaped path ready to be searched in the VFS or file system.
   * @throws IllegalArgumentException if the path is invalid
   */
  @NotNull
  public static String unescapeGitPath(@NotNull String path) throws IllegalArgumentException {
    final String QUOTE = "\"";
    if (path.startsWith(QUOTE) && path.endsWith(QUOTE)) {
      path = path.substring(1, path.length() - 1);
    }
    if (path.indexOf('\\') == -1) return path;

    Charset encoding = Charset.defaultCharset();

    final int l = path.length();
    StringBuilder rc = new StringBuilder(l);
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\\') {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i >= l) {
          throw new IllegalArgumentException("Unterminated escape sequence in the path: " + path);
        }
        final char e = path.charAt(i);
        switch (e) {
          case '\\' -> rc.append('\\');
          case 't' -> rc.append('\t');
          case 'n' -> rc.append('\n');
          case 'r' -> rc.append('\r');
          case 'a' -> rc.append('\u0007');
          case 'b' -> rc.append('\b');
          case 'f' -> rc.append('\f');
          case '"' -> rc.append('"');
          default -> {
            if (isOctal(e)) {
              // collect sequence of characters as a byte array.
              // count bytes first
              int n = 0;
              for (int j = i; j < l; ) {
                if (isOctal(path.charAt(j))) {
                  n++;
                  for (int k = 0; k < 3 && j < l && isOctal(path.charAt(j)); k++) {
                    j++;
                  }
                }
                if (j + 1 >= l || path.charAt(j) != '\\' || !isOctal(path.charAt(j + 1))) {
                  break;
                }
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
              //noinspection AssignmentToForLoopParameter
              i--;
              assert n == b.length;
              // add them to string
              rc.append(new String(b, encoding));
            }
            else {
              throw new IllegalArgumentException("Unknown escape sequence '\\" + path.charAt(i) + "' in the path: " + path);
            }
          }
        }
      }
      else {
        rc.append(c);
      }
    }
    return rc.toString();
  }
}
