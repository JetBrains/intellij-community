/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.ObjectUtils.notNull;
import static java.util.stream.Collectors.groupingBy;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class VcsUtil {
  protected static final char[] ourCharsToBeChopped = new char[]{'/', '\\'};
  private static final Logger LOG = Logger.getInstance("#com.intellij.vcsUtil.VcsUtil");

  public final static String MAX_VCS_LOADED_SIZE_KB = "idea.max.vcs.loaded.size.kb";
  private static final int ourMaxLoadedFileSize = computeLoadedFileSize();

  @NotNull private static final VcsRoot FICTIVE_ROOT = new VcsRoot(null, null);

  public static int getMaxVcsLoadedFileSize() {
    return ourMaxLoadedFileSize;
  }

  private static int computeLoadedFileSize() {
    int result = (int)PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD;
    String userLimitKb = System.getProperty(MAX_VCS_LOADED_SIZE_KB);
    try {
      return userLimitKb != null ? Integer.parseInt(userLimitKb) * 1024 : result;
    }
    catch (NumberFormatException ignored) {
      return result;
    }
  }

  public static void markFileAsDirty(final Project project, final VirtualFile file) {
    VcsDirtyScopeManager.getInstance(project).fileDirty(file);
  }

  public static void markFileAsDirty(final Project project, final FilePath path) {
      VcsDirtyScopeManager.getInstance(project).fileDirty(path);
  }

  public static void markFileAsDirty(final Project project, final String path) {
    final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(new File(path));
    markFileAsDirty( project, filePath );
  }

  public static void refreshFiles(Project project, HashSet<FilePath> paths) {
    for (FilePath path : paths) {
      VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        if (vFile.isDirectory()) {
          markFileAsDirty(project, vFile);
        }
        else {
          vFile.refresh(true, vFile.isDirectory());
        }
      }
    }
  }

  /**
   * @param project Project component
   * @param file    File to check
   * @return true if the given file resides under the root associated with any
   */
  public static boolean isFileUnderVcs(Project project, String file) {
    return getVcsFor(project, getFilePath(file)) != null;
  }

  public static boolean isFileUnderVcs(Project project, FilePath file) {
    return getVcsFor(project, file) != null;
  }

  /**
   * File is considered to be a valid vcs file if it resides under the content
   * root controlled by the given vcs.
   */
  public static boolean isFileForVcs(@NotNull VirtualFile file, Project project, AbstractVcs host) {
    return getVcsFor(project, file) == host;
  }

  //  NB: do not reduce this method to the method above since PLVcsMgr uses
  //      different methods for computing its predicate (since FilePath can
  //      refer to the deleted files).
  public static boolean isFileForVcs(FilePath path, Project project, AbstractVcs host) {
    return getVcsFor(project, path) == host;
  }

  public static boolean isFileForVcs(String path, Project project, AbstractVcs host) {
    return getVcsFor(project, getFilePath(path)) == host;
  }

  @Nullable
  public static AbstractVcs getVcsFor(@NotNull Project project, FilePath filePath) {
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

  @Nullable
  public static VirtualFile getVcsRootFor(@NotNull Project project, FilePath filePath) {
    return computeValue(project, manager -> manager.getVcsRootFor(filePath));
  }

  @Nullable
  public static VirtualFile getVcsRootFor(@NotNull Project project, @Nullable VirtualFile file) {
    return computeValue(project, manager -> manager.getVcsRootFor(file));
  }

  @Nullable
  private static <T> T computeValue(@NotNull Project project, @NotNull Function<ProjectLevelVcsManager, T> provider) {
    return ReadAction.compute(() -> {
      //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
      //  a future task after the component has been disposed.
      T result = null;
      if (!project.isDisposed()) {
        ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
        result = manager != null ? provider.fun(manager) : null;
      }
      return result;
    });
  }

  public static void refreshFiles(final FilePath[] roots, final Runnable runnable) {
    getApplication().assertIsDispatchThread();
    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  public static void refreshFiles(final File[] roots, final Runnable runnable) {
    getApplication().assertIsDispatchThread();
    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  private static File[] collectFilesToRefresh(final FilePath[] roots) {
    final File[] result = new File[roots.length];
    for (int i = 0; i < roots.length; i++) {
      result[i] = roots[i].getIOFile();
    }
    return result;
  }

  private static void refreshFiles(final List<VirtualFile> filesToRefresh, final Runnable runnable) {
    RefreshQueue.getInstance().refresh(true, true, runnable, filesToRefresh);
  }

  private static List<VirtualFile> collectFilesToRefresh(final File[] roots) {
    final ArrayList<VirtualFile> result = new ArrayList<>();
    for (File root : roots) {
      VirtualFile vFile = findFileFor(root);
      if (vFile != null) {
        result.add(vFile);
      } else {
        LOG.info("Failed to find VirtualFile for one of refresh roots: " + root.getAbsolutePath());
      }
    }
    return result;
  }

  @Nullable
  private static VirtualFile findFileFor(final File root) {
    File current = root;
    while (current != null) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(root);
      if (vFile != null) return vFile;
      current = current.getParentFile();
    }

    return null;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull String path) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/')));
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull File file) {
    return ReadAction.compute(() -> LocalFileSystem.getInstance().findFileByIoFile(file));
  }

  @Nullable
  public static VirtualFile getVirtualFileWithRefresh(final File file) {
    if (file == null) return null;
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile result = lfs.findFileByIoFile(file);
    if (result == null) {
      result = lfs.refreshAndFindFileByIoFile(file);
    }
    return result;
  }

  public static String getFileContent(@NotNull String path) {
    return ReadAction.compute(() -> {
      VirtualFile vFile = getVirtualFile(path);
      assert vFile != null;
      return FileDocumentManager.getInstance().getDocument(vFile).getText();
    });
  }

  @Nullable
  public static byte[] getFileByteContent(@NotNull File file) {
    try {
      return FileUtil.loadFileBytes(file);
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  public static FilePath getFilePath(String path) {
    return getFilePath(new File(path));
  }

  public static FilePath getFilePath(@NotNull VirtualFile file) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
  }

  public static FilePath getFilePath(@NotNull File file) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
  }

  public static FilePath getFilePath(@NotNull String path, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePath(path, isDirectory);
  }

  public static FilePath getFilePathOnNonLocal(String path, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(path, isDirectory);
  }

  public static FilePath getFilePath(@NotNull File file, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(file, isDirectory);
  }

  /**
   * @deprecated use {@link #getFilePath(String, boolean)}
   */
  @Deprecated
  public static FilePath getFilePathForDeletedFile(@NotNull String path, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(new File(path), isDirectory);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull VirtualFile parent, @NotNull String name) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(parent, name);
  }

  @NotNull
  public static FilePath getFilePath(@NotNull VirtualFile parent, @NotNull String fileName, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePath(parent, fileName, isDirectory);
  }

  /**
   * Shows message in the status bar.
   *
   * @param project Current project component
   * @param message information message
   */
  public static void showStatusMessage(@NotNull Project project, @Nullable String message) {
    SwingUtilities.invokeLater(() -> {
      if (project.isOpen()) {
        StatusBar.Info.set(message, project);
      }
    });
  }

  /**
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "Rename" operation:
   *         in this case name of files for "before" and "after" revisions must not
   *         coniside.
   */
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
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "New" operation:
   *         "before" revision is obviously NULL, while "after" revision is not.
   */
  public static boolean isChangeForNew(Change change) {
    return (change.getBeforeRevision() == null) && (change.getAfterRevision() != null);
  }

  /**
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "Delete" operation:
   *         "before" revision is NOT NULL, while "after" revision is NULL.
   */
  public static boolean isChangeForDeleted(Change change) {
    return (change.getBeforeRevision() != null) && (change.getAfterRevision() == null);
  }

  public static boolean isChangeForFolder(Change change) {
    ContentRevision revB = change.getBeforeRevision();
    ContentRevision revA = change.getAfterRevision();
    return (revA != null && revA.getFile().isDirectory()) || (revB != null && revB.getFile().isDirectory());
  }

  /**
   * Sort file paths so that paths under the same root are placed from the
   * innermost to the outermost (closest to the root).
   *
   * @param files An array of file paths to be sorted. Sorting is done over the parameter.
   * @return Sorted array of the file paths.
   */
  public static FilePath[] sortPathsFromInnermost(FilePath[] files) {
    return sortPaths(files, -1);
  }

  /**
   * Sort file paths so that paths under the same root are placed from the
   * outermost to the innermost (farest from the root).
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
   * @param e ActionEvent object
   * @return {@code VirtualFile} available in the current context.
   *         Returns not {@code null} if and only if exectly one file is available.
   */
  @Nullable
  public static VirtualFile getOneVirtualFile(AnActionEvent e) {
    VirtualFile[] files = getVirtualFiles(e);
    return (files.length != 1) ? null : files[0];
  }

  /**
   * @param e ActionEvent object
   * @return {@code VirtualFile}s available in the current context.
   *         Returns empty array if there are no available files.
   */
  public static VirtualFile[] getVirtualFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return (files == null) ? VirtualFile.EMPTY_ARRAY : files;
  }

  /**
   * Collects all files which are located in the passed directory.
   *
   * @throws IllegalArgumentException if {@code dir} isn't a directory.
   */
  public static void collectFiles(final VirtualFile dir,
                                  final List<VirtualFile> files,
                                  final boolean recursive,
                                  final boolean addDirectories) {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(VcsBundle.message("exception.text.file.should.be.directory", dir.getPresentableUrl()));
    }

    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          if (addDirectories) {
            files.add(file);
          }
          if (!recursive && !Comparing.equal(file, dir)) {
            return false;
          }
        }
        else if (fileTypeManager == null || file.getFileType() != FileTypes.UNKNOWN) {
          files.add(file);
        }
        return true;
      }
    });
  }

  public static boolean runVcsProcessWithProgress(final VcsRunnable runnable, String progressTitle, boolean canBeCanceled, Project project)
    throws VcsException {
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

  @Nullable
  public static VirtualFile waitForTheFile(@NotNull String path) {
    Ref<VirtualFile> result = Ref.create();

    getApplication().invokeAndWait(
      () -> getApplication().runWriteAction(
        () -> result.set(LocalFileSystem.getInstance().refreshAndFindFileByPath(path))));

    return result.get();
  }

  public static String getCanonicalLocalPath(String localPath) {
    localPath = chopTrailingChars(localPath.trim().replace('\\', '/'), ourCharsToBeChopped);
    if (localPath.length() == 2 && localPath.charAt(1) == ':') {
      localPath += '/';
    }
    return localPath;
  }

  public static String getCanonicalPath( String path )
  {
    String canonPath;
    try {  canonPath = new File( path ).getCanonicalPath();  }
    catch( IOException e ){  canonPath = path;  }
    return canonPath;
  }

  public static String getCanonicalPath( File file )
  {
    String canonPath;
    try {  canonPath = file.getCanonicalPath();  }
    catch (IOException e) {  canonPath = file.getAbsolutePath();  }
    return canonPath;
  }

  /**
   * @param source Source string
   * @param chars  Symbols to be trimmed
   * @return string without all specified chars at the end. For example,
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\'}) is {@code "c:\\my_directory\\//"},
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\','/'}) is {@code "c:\my_directory"}.
   *         Actually this method can be used to normalize file names to chop trailing separator chars.
   */
  public static String chopTrailingChars(String source, char[] chars) {
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

  public static String getShortRevisionString(@NotNull VcsRevisionNumber revision) {
    return revision instanceof ShortVcsRevisionNumber
           ? ((ShortVcsRevisionNumber)revision).toShortString()
           : revision.asString();
  }

  public static VirtualFile[] paths2VFiles(String[] paths) {
    VirtualFile[] files = new VirtualFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      files[i] = getVirtualFile(paths[i]);
    }

    return files;
  }

  private static final String ANNO_ASPECT = "show.vcs.annotation.aspect.";
  //public static boolean isAspectAvailableByDefault(LineAnnotationAspect aspect) {
  //  if (aspect.getId() == null) return aspect.isShowByDefault();
  //  return PropertiesComponent.getInstance().getBoolean(ANNO_ASPECT + aspect.getId(), aspect.isShowByDefault());
  //}

  public static boolean isAspectAvailableByDefault(String id) {
    return isAspectAvailableByDefault(id, true);
  }

  public static boolean isAspectAvailableByDefault(@Nullable String id, boolean defaultValue) {
    if (id == null) return false;
    return PropertiesComponent.getInstance().getBoolean(ANNO_ASPECT + id, defaultValue);
  }

  public static void setAspectAvailability(String aspectID, boolean showByDefault) {
    PropertiesComponent.getInstance().setValue(ANNO_ASPECT + aspectID, String.valueOf(showByDefault));
  }

  public static boolean isPathRemote(String path) {
    final int idx = path.indexOf("://");
    if (idx == -1) {
      final int idx2 = path.indexOf(":\\\\");
      if (idx2 == -1) {
        return false;
      }
      return idx2 > 0;
    }
    return idx > 0;
  }

  public static String getPathForProgressPresentation(@NotNull final File file) {
    return file.getName() + " (" + file.getParent() + ")";
  }

  @NotNull
  public static <T> Map<VcsRoot, List<T>> groupByRoots(@NotNull Project project,
                                                       @NotNull Collection<T> items,
                                                       @NotNull Function<T, FilePath> filePathMapper) {
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);

    return items.stream().collect(groupingBy(item -> notNull(manager.getVcsRootObjectFor(filePathMapper.fun(item)), FICTIVE_ROOT)));
  }

  @NotNull
  public static Collection<VcsDirectoryMapping> findRoots(@NotNull VirtualFile rootDir, @NotNull Project project)
    throws IllegalArgumentException {
    if (!rootDir.isDirectory()) {
      throw new IllegalArgumentException(
        "Can't find VCS at the target file system path. Reason: expected to find a directory there but it's not. The path: "
        + rootDir.getParent()
      );
    }
    Collection<VcsRoot> roots = ServiceManager.getService(project, VcsRootDetector.class).detect(rootDir);
    Collection<VcsDirectoryMapping> result = ContainerUtilRt.newArrayList();
    for (VcsRoot vcsRoot : roots) {
      VirtualFile vFile = vcsRoot.getPath();
      AbstractVcs rootVcs = vcsRoot.getVcs();
      if (rootVcs != null && vFile != null) {
        result.add(new VcsDirectoryMapping(vFile.getPath(), rootVcs.getName()));
      }
    }
    return result;
  }

  @NotNull
  public static List<VcsDirectoryMapping> addMapping(@NotNull List<VcsDirectoryMapping> existingMappings,
                                                     @NotNull String path,
                                                     @NotNull String vcs) {
    List<VcsDirectoryMapping> mappings = new ArrayList<>(existingMappings);
    for (Iterator<VcsDirectoryMapping> iterator = mappings.iterator(); iterator.hasNext(); ) {
      VcsDirectoryMapping mapping = iterator.next();
      if (mapping.isDefaultMapping() && StringUtil.isEmptyOrSpaces(mapping.getVcs())) {
        LOG.debug("Removing <Project> -> <None> mapping");
        iterator.remove();
      }
      else if (FileUtil.pathsEqual(mapping.getDirectory(), path)) {
        if (!StringUtil.isEmptyOrSpaces(mapping.getVcs())) {
          LOG.warn("Substituting existing mapping [" + path + "] -> [" + mapping.getVcs() + "] with [" + vcs + "]");
        }
        else {
          LOG.debug("Removing [" + path + "] -> <None> mapping");
        }
        iterator.remove();
      }
    }
    mappings.add(new VcsDirectoryMapping(path, vcs));
    return mappings;
  }
}
