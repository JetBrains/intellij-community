// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
@ApiStatus.NonExtendable
public final class VcsUtil {
  private static final char[] ourCharsToBeChopped = {'/', '\\'};
  private static final Logger LOG = Logger.getInstance(VcsUtil.class);

  public static final @NonNls String MAX_VCS_LOADED_SIZE_KB = "idea.max.vcs.loaded.size.kb";
  private static final int ourMaxLoadedFileSize = computeLoadedFileSize();

  private static final int MAX_COMMIT_MESSAGE_LENGTH = 50000;
  private static final int MAX_COMMIT_MESSAGE_LINES = 3000;

  public static int getMaxVcsLoadedFileSize() {
    return ourMaxLoadedFileSize;
  }

  private static int computeLoadedFileSize() {
    long result = PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD;
    try {
      String userLimitKb = System.getProperty(MAX_VCS_LOADED_SIZE_KB);
      if (userLimitKb != null) {
        result = Integer.parseInt(userLimitKb) * 1024L;
      }
    }
    catch (NumberFormatException ignored) {
    }

    return (int)Math.min(result, Integer.MAX_VALUE);
  }

  public static void markFileAsDirty(final Project project, @NonNls String path) {
    final FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(new File(path));
    VcsDirtyScopeManager.getInstance(project).fileDirty(filePath);
  }

  /**
   * @return true if the given file resides under the root associated with any vcs
   */
  public static boolean isFileUnderVcs(Project project, @NotNull @NonNls String file) {
    return getVcsFor(project, getFilePath(file)) != null;
  }

  public static boolean isFileUnderVcs(Project project, @NotNull FilePath file) {
    return getVcsFor(project, file) != null;
  }

  /**
   * File is considered to be a valid vcs file if it resides under the content
   * root controlled by the given vcs.
   */
  public static boolean isFileForVcs(@NotNull VirtualFile file, @NotNull Project project, @Nullable AbstractVcs host) {
    return getVcsFor(project, file) == host;
  }

  public static boolean isFileForVcs(@NotNull FilePath path, @NotNull Project project, @Nullable AbstractVcs host) {
    return getVcsFor(project, path) == host;
  }

  public static boolean isFileForVcs(@NotNull @NonNls String path, Project project, AbstractVcs host) {
    return getVcsFor(project, getFilePath(path)) == host;
  }

  @Nullable
  public static AbstractVcs getVcsFor(@NotNull Project project, @NotNull FilePath filePath) {
    return computeValue(project, manager -> manager.getVcsFor(filePath));
  }

  @Nullable
  public static AbstractVcs getVcsFor(@NotNull Project project, @NotNull VirtualFile file) {
    return computeValue(project, manager -> manager.getVcsFor(file));
  }

  @Nullable
  public static AbstractVcs findVcsByKey(@NotNull Project project, @NotNull VcsKey key) {
    return ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName());
  }

  @Nullable
  public static AbstractVcs findVcs(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    VcsKey key = e.getData(VcsDataKeys.VCS);
    if (key == null) return null;

    return findVcsByKey(project, key);
  }

  public static @Nullable VirtualFile getVcsRootFor(@NotNull Project project, @Nullable FilePath filePath) {
    return computeValue(project, manager -> manager.getVcsRootFor(filePath));
  }

  public static @Nullable VirtualFile getVcsRootFor(@NotNull Project project, @Nullable VirtualFile file) {
    return computeValue(project, manager -> manager.getVcsRootFor(file));
  }

  @Nullable
  private static <T> T computeValue(@NotNull Project project,
                                    @NotNull java.util.function.Function<? super ProjectLevelVcsManager, ? extends T> provider) {
    return ReadAction.compute(() -> {
      //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
      //  a future task after the component has been disposed.
      T result = null;
      if (!project.isDisposed()) {
        ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
        result = manager != null ? provider.apply(manager) : null;
      }
      return result;
    });
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull @NonNls String path) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/')));
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull File file) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByIoFile(file));
  }

  @Nullable
  public static VirtualFile getVirtualFileWithRefresh(@Nullable File file) {
    if (file == null) return null;
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile result = lfs.findFileByIoFile(file);
    if (result == null) {
      result = lfs.refreshAndFindFileByIoFile(file);
    }
    return result;
  }

  @NlsSafe
  public static String getFileContent(@NotNull @NonNls String path) {
    return ReadAction.compute(() -> {
      VirtualFile vFile = getVirtualFile(path);
      assert vFile != null;
      return FileDocumentManager.getInstance().getDocument(vFile).getText();
    });
  }

  public static byte @Nullable [] getFileByteContent(@NotNull File file) {
    try {
      return FileUtil.loadFileBytes(file);
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  @NotNull
  public static FilePath getFilePath(@NotNull @NonNls String path) {
    return getFilePath(new File(path));
  }

  @NotNull
  public static FilePath getFilePath(@NotNull VirtualFile file) {
    return VcsContextFactory.getInstance().createFilePathOn(file);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull File file) {
    return VcsContextFactory.getInstance().createFilePathOn(file);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull Path path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  @NotNull
  public static FilePath getFilePathOnNonLocal(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePathOnNonLocal(path, isDirectory);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull File file, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePathOn(file, isDirectory);
  }

  /**
   * @deprecated use {@link #getFilePath(String, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull FilePath getFilePathForDeletedFile(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull VirtualFile parent, @NotNull @NonNls String name) {
    return VcsContextFactory.getInstance().createFilePathOn(parent, name);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull VirtualFile parent, @NotNull @NonNls String fileName, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(parent, fileName, isDirectory);
  }

  @Nullable
  public static Icon getIcon(@Nullable Project project, @NotNull FilePath filePath) {
    if (project != null && project.isDisposed()) return null;
    VirtualFile virtualFile = filePath.getVirtualFile();
    if (virtualFile != null) return IconUtil.getIcon(virtualFile, 0, project);
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(filePath.getName());
    return fileType.getIcon();
  }

  /**
   * @return Return true if the "Change" object is created for "Rename" operation:
   * in this case name of files for "before" and "after" revisions must not coincide.
   * @deprecated See {@link Change#getType()}
   */
  @Deprecated(forRemoval = true)
  public static boolean isRenameChange(Change change) {
    boolean isRenamed = false;
    ContentRevision before = change.getBeforeRevision();
    ContentRevision after = change.getAfterRevision();
    if (before != null && after != null) {
      String prevFile = getCanonicalLocalPath(before.getFile().getPath());
      String newFile = getCanonicalLocalPath(after.getFile().getPath());
      isRenamed = !prevFile.equals(newFile);
    }
    return isRenamed;
  }

  /**
   * @return Return true if the "Change" object is created for "New" operation:
   * "before" revision is NULL, while "after" revision is NOT NULL.
   * @deprecated See {@link Change#getType()}
   */
  @Deprecated(forRemoval = true)
  public static boolean isChangeForNew(Change change) {
    return change.getBeforeRevision() == null && change.getAfterRevision() != null;
  }

  /**
   * @return Return true if the "Change" object is created for "Delete" operation:
   * "before" revision is NOT NULL, while "after" revision is NULL.
   * @deprecated See {@link Change#getType()}
   */
  @Deprecated(forRemoval = true)
  public static boolean isChangeForDeleted(Change change) {
    return change.getBeforeRevision() != null && change.getAfterRevision() == null;
  }

  public static boolean isChangeForFolder(Change change) {
    ContentRevision revB = change.getBeforeRevision();
    ContentRevision revA = change.getAfterRevision();
    return revA != null && revA.getFile().isDirectory() || revB != null && revB.getFile().isDirectory();
  }

  /**
   * Sort file paths so that paths under the same root are placed from the
   * outermost to the innermost (farthest from the root).
   *
   * @param files An array of file paths to be sorted. Sorting is done over the parameter.
   * @return Sorted array of the file paths.
   */
  public static FilePath[] sortPathsFromOutermost(FilePath[] files) {
    return sortPaths(files, 1);
  }

  private static FilePath[] sortPaths(FilePath[] files, final int sign) {
    Arrays.sort(files, (file1, file2) -> sign * file1.getPath().compareTo(file2.getPath()));
    return files;
  }

  /**
   * @return {@code VirtualFile} available in the current context.
   * Returns not {@code null} if and only if exactly one file is available.
   */
  @Nullable
  public static VirtualFile getOneVirtualFile(@NotNull AnActionEvent e) {
    VirtualFile[] files = getVirtualFiles(e);
    return files.length != 1 ? null : files[0];
  }

  /**
   * @return {@code VirtualFile}s available in the current context.
   */
  public static VirtualFile @NotNull [] getVirtualFiles(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files == null ? VirtualFile.EMPTY_ARRAY : files;
  }

  /**
   * @deprecated Use {@link ProgressManager#runProcessWithProgressSynchronously(ThrowableComputable, String, boolean, Project)}
   * and other run methods from the ProgressManager.
   */
  @Deprecated(forRemoval = true)
  public static boolean runVcsProcessWithProgress(@NotNull VcsRunnable runnable,
                                                  @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                  boolean canBeCanceled,
                                                  @Nullable Project project) throws VcsException {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      final Ref<VcsException> ex = new Ref<>();
      boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        try {
          runnable.run();
        }
        catch (VcsException e) {
          ex.set(e);
        }
      }, progressTitle, canBeCanceled, project);
      if (!ex.isNull()) {
        throw ex.get();
      }
      return result;
    }
    else {
      runnable.run();
      return true;
    }
  }

  public static <T> T computeWithModalProgress(@Nullable Project project,
                                               @NotNull @NlsContexts.DialogTitle String title,
                                               boolean canBeCancelled,
                                               @NotNull ThrowableConvertor<? super ProgressIndicator, T, ? extends VcsException> computable)
    throws VcsException {
    return ProgressManager.getInstance().run(new Task.WithResult<T, VcsException>(project, title, canBeCancelled) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws VcsException {
        return computable.convert(indicator);
      }
    });
  }

  @NonNls
  public static String getCanonicalLocalPath(@NonNls String localPath) {
    localPath = chopTrailingChars(localPath.trim().replace('\\', '/'), ourCharsToBeChopped);
    if (localPath.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(localPath)) {
      localPath += '/';
    }
    return localPath;
  }

  @NonNls
  public static String getCanonicalPath(@NonNls String path) {
    String canonPath;
    try {
      canonPath = new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      canonPath = path;
    }
    return canonPath;
  }

  @NonNls
  public static String getCanonicalPath(File file) {
    String canonPath;
    try {
      canonPath = file.getCanonicalPath();
    }
    catch (IOException e) {
      canonPath = file.getAbsolutePath();
    }
    return canonPath;
  }

  /**
   * @param source Source string
   * @param chars  Symbols to be trimmed
   * @return string without all specified chars at the end. For example,
   * <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\'}) is {@code "c:\\my_directory\\//"},
   * <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\','/'}) is {@code "c:\my_directory"}.
   * Actually this method can be used to normalize file names to chop trailing separator chars.
   */
  @NonNls
  public static String chopTrailingChars(@NonNls String source, char[] chars) {
    StringBuilder sb = new StringBuilder(source);
    while (true) {
      boolean atLeastOneCharWasChopped = false;
      for (int i = 0; i < chars.length && sb.length() > 0; i++) {
        if (sb.charAt(sb.length() - 1) == chars[i]) {
          sb.deleteCharAt(sb.length() - 1);
          atLeastOneCharWasChopped = true;
        }
      }
      if (!atLeastOneCharWasChopped) {
        break;
      }
    }
    return sb.toString();
  }

  @Nls
  public static String getShortRevisionString(@NotNull VcsRevisionNumber revision) {
    return revision instanceof ShortVcsRevisionNumber
           ? ((ShortVcsRevisionNumber)revision).toShortString()
           : revision.asString();
  }

  private static final @NonNls String ANNO_ASPECT = "show.vcs.annotation.aspect.";

  public static boolean isAspectAvailableByDefault(@Nullable @NonNls String id) {
    return isAspectAvailableByDefault(id, true);
  }

  public static boolean isAspectAvailableByDefault(@Nullable @NonNls String id, boolean defaultValue) {
    if (id == null) return false;
    return PropertiesComponent.getInstance().getBoolean(ANNO_ASPECT + id, defaultValue);
  }

  public static void setAspectAvailability(@Nullable @NonNls String aspectID, boolean showByDefault) {
    PropertiesComponent.getInstance().setValue(ANNO_ASPECT + aspectID, String.valueOf(showByDefault));
  }

  @Nls
  public static String getPathForProgressPresentation(@NotNull File file) {
    return file.getName() + " (" + FileUtil.getLocationRelativeToUserHome(file.getParent()) + ")";
  }

  @NlsSafe
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

  @NotNull
  public static @NlsSafe String getPresentablePath(@Nullable Project project,
                                                   @NotNull VirtualFile file,
                                                   boolean useRelativeRootPaths,
                                                   boolean acceptEmptyPath) {
    return getPresentablePath(project, getFilePath(file), useRelativeRootPaths, acceptEmptyPath);
  }

  @NotNull
  public static @NlsSafe String getPresentablePath(@Nullable Project project,
                                                   @NotNull FilePath filePath,
                                                   boolean useRelativeRootPaths,
                                                   boolean acceptEmptyPath) {
    String projectDir = project != null ? project.getBasePath() : null;
    if (projectDir != null) {
      if (useRelativeRootPaths) {
        String relativePath = getRootRelativePath(project, projectDir, filePath, acceptEmptyPath);
        if (relativePath != null) return toSystemDependentName(relativePath);
      }

      String path = filePath.getPath();
      String relativePath = getRelativePathIfSuccessor(projectDir, path);
      if (relativePath != null) {
        return toSystemDependentName(VcsBundle.message("label.relative.project.path.presentation", relativePath));
      }
    }

    return FileUtil.getLocationRelativeToUserHome(toSystemDependentName(filePath.getPath()));
  }

  @Nullable
  @SystemIndependent
  private static String getRootRelativePath(@NotNull Project project,
                                            @NotNull String projectBaseDir,
                                            @NotNull FilePath filePath,
                                            boolean acceptEmptyPath) {
    if (project.isDisposed()) return null;
    String path = filePath.getPath();

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    VirtualFile root = vcsManager.getVcsRootFor(filePath);
    if (root == null) return null;

    String rootPath = root.getPath();

    VcsRoot[] roots = vcsManager.getAllVcsRoots();
    if (roots.length == 1) {
      if (rootPath.equals(path)) return acceptEmptyPath ? "" : root.getName();
      return getRelativePathIfSuccessor(rootPath, path);
    }

    if (projectBaseDir.equals(path)) {
      return root.getName();
    }
    String relativePath = getRelativePathIfSuccessor(projectBaseDir, path);
    if (relativePath == null) return null;
    return projectBaseDir.equals(rootPath) ? root.getName() + '/' + relativePath : relativePath;
  }

  @Nullable
  private static String getRelativePathIfSuccessor(@NotNull String ancestor, @NotNull String path) {
    return FileUtil.isAncestor(ancestor, path, true) ? FileUtil.getRelativePath(ancestor, path, '/') : null;
  }

  @NotNull
  public static <T> Map<VcsRoot, List<T>> groupByRoots(@NotNull Project project,
                                                       @NotNull Collection<? extends T> items,
                                                       @NotNull Function<? super T, ? extends FilePath> filePathMapper) {
    return groupByRoots(project, items, false, filePathMapper);
  }

  @NotNull
  public static <T> Map<VcsRoot, List<T>> groupByRoots(@NotNull Project project,
                                                       @NotNull Collection<? extends T> items,
                                                       boolean putNonVcs,
                                                       @NotNull Function<? super T, ? extends FilePath> filePathMapper) {
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);

    Map<VcsRoot, List<T>> map = new HashMap<>();
    for (T item : items) {
      VcsRoot vcsRoot = manager.getVcsRootObjectFor(filePathMapper.fun(item));
      if (vcsRoot != null || putNonVcs) {
        List<T> list = map.computeIfAbsent(vcsRoot, key -> new ArrayList<>());
        list.add(item);
      }
    }
    return map;
  }

  @NotNull
  public static List<VcsDirectoryMapping> addMapping(@NotNull List<? extends VcsDirectoryMapping> existingMappings,
                                                     @NotNull @NonNls String path,
                                                     @NotNull @NonNls String vcs) {
    return addMapping(existingMappings, new VcsDirectoryMapping(path, vcs));
  }

  @NotNull
  public static List<VcsDirectoryMapping> addMapping(@NotNull List<? extends VcsDirectoryMapping> existingMappings,
                                                     @NotNull VcsDirectoryMapping newMapping) {
    List<VcsDirectoryMapping> mappings = new ArrayList<>(existingMappings);
    for (Iterator<VcsDirectoryMapping> iterator = mappings.iterator(); iterator.hasNext(); ) {
      VcsDirectoryMapping mapping = iterator.next();
      if (mapping.isDefaultMapping() && mapping.isNoneMapping()) {
        LOG.debug("Removing <Project> -> <None> mapping");
        iterator.remove();
      }
      else if (FileUtil.pathsEqual(mapping.getDirectory(), newMapping.getDirectory())) {
        if (!StringUtil.isEmptyOrSpaces(mapping.getVcs())) {
          if (mapping.getVcs().equals(newMapping.getVcs())) {
            LOG.debug(String.format("Substituting existing mapping with identical [%s] -> [%s]",
                                    mapping.getDirectory(), mapping.getVcs()));
          }
          else {
            LOG.warn(String.format("Substituting existing mapping [%s] -> [%s] with [%s]",
                                   mapping.getDirectory(), mapping.getVcs(), newMapping.getVcs()));
          }
        }
        else {
          LOG.debug(String.format("Removing [%s] -> <None> mapping", mapping.getDirectory()));
        }
        iterator.remove();
      }
    }
    mappings.add(newMapping);
    return mappings;
  }

  /**
   * Get path to the file in the last commit. If file was renamed locally, returns the previous file path.
   *
   * @param project the context project
   * @param path    the path to check
   * @return the name of file in the last commit or argument
   */
  @NotNull
  public static FilePath getLastCommitPath(@NotNull Project project, @NotNull FilePath path) {
    if (project.isDefault()) return path;

    Change change = ChangeListManager.getInstance(project).getChange(path);
    if (change == null || change.getType() != Change.Type.MOVED || change.getBeforeRevision() == null) {
      return path;
    }

    return change.getBeforeRevision().getFile();
  }

  @NotNull
  public static Set<String> getVcsIgnoreFileNames(@NotNull Project project) {
    Set<String> set = new HashSet<>();
    for (IgnoredFileContentProvider provider : IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.getExtensionList(project)) {
      set.add(provider.getFileName());
    }
    return set;
  }

  @Nls
  @NotNull
  public static String joinWithAnd(@NotNull List<@Nls String> strings, int limit) {
    int size = strings.size();
    if (size == 0) return "";
    if (size == 1) return strings.get(0);
    if (size == 2) return VcsBundle.message("sequence.concatenation.a.and.b", strings.get(0), strings.get(1));

    boolean isLimited = limit >= 2 && limit < size;
    int listCount = (isLimited ? limit : size) - 1;

    @Nls StringBuilder sb = new StringBuilder();
    for (int i = 0; i < listCount; i++) {
      if (i != 0) sb.append(VcsBundle.message("sequence.concatenation.separator"));
      sb.append(strings.get(i));
    }

    if (isLimited) {
      sb.append(VcsBundle.message("sequence.concatenation.tail.n.others", size - limit + 1));
    }
    else {
      sb.append(VcsBundle.message("sequence.concatenation.tail", strings.get(size - 1)));
    }
    return sb.toString();
  }

  @NlsSafe
  @NotNull
  public static String trimCommitMessageToSaneSize(@NotNull @NlsSafe String message) {
    int nthLine = nthIndexOf(message, '\n', MAX_COMMIT_MESSAGE_LINES);
    if (nthLine != -1 && nthLine < MAX_COMMIT_MESSAGE_LENGTH) {
      return trimCommitMessageAt(message, nthLine);
    }
    if (message.length() > MAX_COMMIT_MESSAGE_LENGTH + 50) {
      return trimCommitMessageAt(message, MAX_COMMIT_MESSAGE_LENGTH);
    }
    return message;
  }

  @NlsSafe
  private static String trimCommitMessageAt(@NotNull @NlsSafe String message, int index) {
    return VcsBundle.message("text.commit.message.truncated.by.ide.name", message.substring(0, index),
                             ApplicationNamesInfo.getInstance().getProductName());
  }

  private static int nthIndexOf(@NotNull @NonNls String text, char c, int n) {
    assert n > 0;
    int length = text.length();
    int count = 0;
    for (int i = 0; i < length; i++) {
      if (text.charAt(i) == c) {
        count++;
        if (count == n) return i;
      }
    }
    return -1;
  }

  /**
   * Helper that allows to avoid potential O(N*M) in {@link AbstractSet#removeAll(Collection)} due to {@code list.contains(c)} calls.
   */
  public static <T> boolean removeAllFromSet(@NotNull Set<T> set, @NotNull Collection<? extends T> toRemove) {
    boolean modified = false;
    for (T value : toRemove) {
      modified |= set.remove(value);
    }
    return modified;
  }
}
