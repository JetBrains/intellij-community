// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.vcs.VcsSymlinkResolver;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
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
    long result = FileSizeLimit.getDefaultContentLoadLimit();
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

  /**
   * @return true if the given file resides under the root associated with any vcs
   */
  public static boolean isFileUnderVcs(Project project, @NotNull @NonNls String file) {
    return getVcsFor(project, getFilePath(file, false)) != null;
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
    return getVcsFor(project, getFilePath(path, false)) == host;
  }

  public static @Nullable AbstractVcs getVcsFor(@NotNull Project project, @NotNull FilePath filePath) {
    return computeValue(project, manager -> manager.getVcsFor(filePath));
  }

  public static @Nullable AbstractVcs getVcsFor(@NotNull Project project, @NotNull VirtualFile file) {
    return computeValue(project, manager -> manager.getVcsFor(file));
  }

  public static @Nullable AbstractVcs findVcsByKey(@NotNull Project project, @NotNull VcsKey key) {
    return ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName());
  }

  public static @Nullable AbstractVcs findVcs(@NotNull AnActionEvent e) {
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

  private static @Nullable <T> T computeValue(@NotNull Project project,
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

  public static @Nullable VirtualFile getVirtualFile(@NotNull @NonNls String path) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/')));
  }

  public static @Nullable VirtualFile getVirtualFile(@NotNull File file) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByIoFile(file));
  }

  public static @Nullable VirtualFile getVirtualFileWithRefresh(@Nullable File file) {
    if (file == null) return null;
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile result = lfs.findFileByIoFile(file);
    if (result == null) {
      result = lfs.refreshAndFindFileByIoFile(file);
    }
    return result;
  }

  public static @NlsSafe String getFileContent(@NotNull @NonNls String path) {
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

  /**
   * @deprecated This method will detect {@link FilePath#isDirectory()} using NIO.
   * Avoid using the method, if {@code isDirectory} is known from context or not important.
   */
  @Deprecated
  public static @NotNull FilePath getFilePath(@NotNull @NonNls String path) {
    return getFilePath(new File(path));
  }

  public static @NotNull FilePath getFilePath(@NotNull VirtualFile file) {
    return VcsContextFactory.getInstance().createFilePathOn(file);
  }

  /**
   * @deprecated This method will detect {@link FilePath#isDirectory()} using NIO.
   * Avoid using the method, if {@code isDirectory} is known from context or not important.
   */
  @Deprecated
  public static @NotNull FilePath getFilePath(@NotNull File file) {
    return VcsContextFactory.getInstance().createFilePathOn(file);
  }

  public static @NotNull FilePath getFilePath(@NotNull Path path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  public static @NotNull FilePath getFilePath(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  public static @NotNull FilePath getFilePathOnNonLocal(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePathOnNonLocal(path, isDirectory);
  }

  public static @NotNull FilePath getFilePath(@NotNull File file, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePathOn(file, isDirectory);
  }

  /**
   * @deprecated use {@link #getFilePath(String, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull FilePath getFilePathForDeletedFile(@NotNull @NonNls String path, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(path, isDirectory);
  }

  public static @NotNull FilePath getFilePath(@NotNull VirtualFile parent, @NotNull @NonNls String name) {
    return VcsContextFactory.getInstance().createFilePathOn(parent, name);
  }

  public static @NotNull FilePath getFilePath(@NotNull VirtualFile parent, @NotNull @NonNls String fileName, boolean isDirectory) {
    return VcsContextFactory.getInstance().createFilePath(parent, fileName, isDirectory);
  }

  public static @Nullable Icon getIcon(@Nullable Project project, @NotNull FilePath filePath) {
    if (project != null && project.isDisposed()) return null;
    VirtualFile virtualFile = filePath.getVirtualFile();
    if (virtualFile != null) return IconUtil.getIcon(virtualFile, 0, project);
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(filePath.getName());
    return fileType.getIcon();
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
  public static @Nullable VirtualFile getOneVirtualFile(@NotNull AnActionEvent e) {
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

  public static @NonNls String getCanonicalLocalPath(@NonNls String localPath) {
    localPath = chopTrailingChars(localPath.trim().replace('\\', '/'), ourCharsToBeChopped);
    if (localPath.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(localPath)) {
      localPath += '/';
    }
    return localPath;
  }

  public static @NonNls String getCanonicalPath(@NonNls String path) {
    String canonPath;
    try {
      canonPath = new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      canonPath = path;
    }
    return canonPath;
  }

  public static @NonNls String getCanonicalPath(File file) {
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
  public static @NonNls String chopTrailingChars(@NonNls String source, char[] chars) {
    StringBuilder sb = new StringBuilder(source);
    while (true) {
      boolean atLeastOneCharWasChopped = false;
      for (int i = 0; i < chars.length && !sb.isEmpty(); i++) {
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

  public static @Nls String getShortRevisionString(@NotNull VcsRevisionNumber revision) {
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

  public static @Nls String getPathForProgressPresentation(@NotNull File file) {
    return file.getName() + " (" + FileUtil.getLocationRelativeToUserHome(file.getParent()) + ")";
  }

  public static @NlsSafe @NotNull String getShortVcsRootName(@NotNull Project project, @NotNull VirtualFile root) {
    if (project.isDisposed()) return root.getName();
    return ProjectLevelVcsManager.getInstance(project).getShortNameForVcsRoot(root);
  }

  public static @NotNull @NlsSafe String getPresentablePath(@Nullable Project project,
                                                            @NotNull VirtualFile file,
                                                            boolean useRelativeRootPaths,
                                                            boolean acceptEmptyPath) {
    return getPresentablePath(project, getFilePath(file), useRelativeRootPaths, acceptEmptyPath);
  }

  public static @NotNull @NlsSafe String getPresentablePath(@Nullable Project project,
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

  private static @Nullable @SystemIndependent String getRootRelativePath(@NotNull Project project,
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

  private static @Nullable String getRelativePathIfSuccessor(@NotNull String ancestor, @NotNull String path) {
    return FileUtil.isAncestor(ancestor, path, true) ? FileUtil.getRelativePath(ancestor, path, '/') : null;
  }

  public static @NotNull <T> Map<VcsRoot, List<T>> groupByRoots(@NotNull Project project,
                                                                @NotNull Collection<? extends T> items,
                                                                @NotNull Function<? super T, ? extends FilePath> filePathMapper) {
    return groupByRoots(project, items, false, filePathMapper);
  }

  public static @NotNull <T> Map<VcsRoot, List<T>> groupByRoots(@NotNull Project project,
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

  public static @NotNull List<VcsDirectoryMapping> addMapping(@NotNull List<? extends VcsDirectoryMapping> existingMappings,
                                                              @NotNull @NonNls String path,
                                                              @NotNull @NonNls String vcs) {
    return addMapping(existingMappings, new VcsDirectoryMapping(path, vcs));
  }

  public static @NotNull List<VcsDirectoryMapping> addMapping(@NotNull List<? extends VcsDirectoryMapping> existingMappings,
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

  public static @NotNull VirtualFile resolveSymlinkIfNeeded(@NotNull Project project, @NotNull VirtualFile file) {
    VirtualFile symlink = resolveSymlink(project, file);
    return symlink != null ? symlink : file;
  }

  public static @Nullable VirtualFile resolveSymlink(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null) return null;
    for (VcsSymlinkResolver resolver : VcsSymlinkResolver.EP_NAME.getExtensionList(project)) {
      if (resolver.isEnabled()) {
        VirtualFile symlink = resolver.resolveSymlink(file);
        if (symlink != null) return symlink;
      }
    }
    return null;
  }

  /**
   * Get path to the file in the last commit. If file was renamed locally, returns the previous file path.
   *
   * @param project the context project
   * @param path    the path to check
   * @return the name of file in the last commit or argument
   */
  public static @NotNull FilePath getLastCommitPath(@NotNull Project project, @NotNull FilePath path) {
    if (project.isDefault()) return path;

    Change change = ChangeListManager.getInstance(project).getChange(path);
    if (change == null || change.getType() != Change.Type.MOVED || change.getBeforeRevision() == null) {
      return path;
    }

    return change.getBeforeRevision().getFile();
  }

  public static @NotNull Set<String> getVcsIgnoreFileNames(@NotNull Project project) {
    Set<String> set = new HashSet<>();
    for (IgnoredFileContentProvider provider : IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.getExtensionList(project)) {
      set.add(provider.getFileName());
    }
    return set;
  }

  public static @Nls @NotNull String joinWithAnd(@NotNull List<@Nls String> strings, int limit) {
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

  public static @NlsSafe @NotNull String trimCommitMessageToSaneSize(@NotNull @NlsSafe String message) {
    int nthLine = nthIndexOf(message, '\n', MAX_COMMIT_MESSAGE_LINES);
    if (nthLine != -1 && nthLine < MAX_COMMIT_MESSAGE_LENGTH) {
      return trimCommitMessageAt(message, nthLine);
    }
    if (message.length() > MAX_COMMIT_MESSAGE_LENGTH + 50) {
      return trimCommitMessageAt(message, MAX_COMMIT_MESSAGE_LENGTH);
    }
    return message;
  }

  private static @NlsSafe String trimCommitMessageAt(@NotNull @NlsSafe String message, int index) {
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

  public static boolean shouldDetectVcsMappingsFor(@NotNull Project project) {
    return Registry.is("vcs.detect.vcs.mappings.automatically") &&
           VcsSharedProjectSettings.getInstance(project).isDetectVcsMappingsAutomatically();
  }
}
