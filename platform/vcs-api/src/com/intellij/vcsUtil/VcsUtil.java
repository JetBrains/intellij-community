/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class VcsUtil {
  protected static final char[] ourCharsToBeChopped = new char[]{'/', '\\'};
  private static final Logger LOG = Logger.getInstance("#com.intellij.vcsUtil.VcsUtil");


  /**
   * Call "fileDirty" in the read action.
   */
  public static void markFileAsDirty(final Project project, final VirtualFile file) {
    final VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {  mgr.fileDirty(file);  }
    });
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
  public static AbstractVcs getVcsFor(final Project project, final FilePath file) {
    final AbstractVcs[] vcss = new AbstractVcs[ 1 ];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
        //  a future task after the component has been disposed.
        if( !project.isDisposed() )
        {
          ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
          vcss[ 0 ] = (mgr != null) ? mgr.getVcsFor(file) : null;
        }
      }
    });
    return vcss[ 0 ];
  }

  @Nullable
  public static AbstractVcs getVcsFor(final Project project, @NotNull final VirtualFile file) {
    final AbstractVcs[] vcss = new AbstractVcs[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
        //  a future task after the component has been disposed.
        if( !project.isDisposed() )
        {
          ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
          vcss[ 0 ] = (mgr != null) ? mgr.getVcsFor(file) : null;
        }
      }
    });
    return vcss[0];
  }

  @Nullable
  public static VirtualFile getVcsRootFor(final Project project, final FilePath file) {
    final VirtualFile[] roots = new VirtualFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
        //  a future task after the component has been disposed.
        if( !project.isDisposed() )
        {
          ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
          roots[ 0 ] = (mgr != null) ? mgr.getVcsRootFor( file ) : null;
        }
      }
    });
    return roots[0];
  }

  @Nullable
  public static VirtualFile getVcsRootFor(final Project project, final VirtualFile file) {
    final VirtualFile[] roots = new VirtualFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        //  IDEADEV-17916, when e.g. ContentRevision.getContent is called in
        //  a future task after the component has been disposed.
        if( !project.isDisposed() )
        {
          ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
          roots[ 0 ] = (mgr != null) ? mgr.getVcsRootFor( file ) : null;
        }
      }
    });
    return roots[0];
  }

  public static void refreshFiles(final FilePath[] roots, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  public static void refreshFiles(final File[] roots, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    RefreshQueue.getInstance().refresh(true, true, runnable, VfsUtil.toVirtualFileArray(filesToRefresh));
  }

  private static List<VirtualFile> collectFilesToRefresh(final File[] roots) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
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
  public static VirtualFile getVirtualFile(final String path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
      }
    });
  }

  @Nullable
  public static VirtualFile getVirtualFile(final File file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    });
  }

  @Nullable
  public static VirtualFile getVirtualFileWithRefresh(final File file) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile result = lfs.findFileByIoFile(file);
    if (result == null) {
      result = lfs.refreshAndFindFileByIoFile(file);
    }
    return result;
  }

  public static String getFileContent(final String path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        VirtualFile vFile = VcsUtil.getVirtualFile(path);
        final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        return doc.getText();
      }
    });
  }

  //  FileDocumentManager has difficulties in loading the content for files
  //  which are outside the project structure?
  public static byte[] getFileByteContent(final File file) throws IOException {
    return ApplicationManager.getApplication().runReadAction(new Computable<byte[]>() {
      public byte[] compute() {
        byte[] content;
        try {
          content = FileUtil.loadFileBytes(file);
        }
        catch (IOException e) {
          content = null;
        }
        return content;
      }
    });
  }

  public static FilePath getFilePath(String path) {
    return getFilePath(new File(path));
  }

  public static FilePath getFilePath(File file) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
  }

  public static FilePath getFilePath(String path, boolean isDirectory) {
    return getFilePath(new File(path), isDirectory);
  }

  public static FilePath getFilePath(File file, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(file, isDirectory);
  }

  public static FilePath getFilePathForDeletedFile(String path, boolean isDirectory) {
    return VcsContextFactory.SERVICE.getInstance().createFilePathOnDeleted(new File(path), isDirectory);
  }

  /**
   * Shows error message with specified message text and title.
   * The parent component is the root frame.
   *
   * @param project Current project component
   * @param message information message
   * @param title   Dialog title
   */
  public static void showErrorMessage(final Project project, final String message, final String title)
  {
    Runnable task = new Runnable() {  public void run() {  Messages.showErrorDialog( project, message, title );  } };
    if( ApplicationManager.getApplication().isDispatchThread() )
      task.run();
    else
      ApplicationManager.getApplication().invokeLater( task );
  }

  /**
   * Shows message in the status bar.
   *
   * @param project Current project component
   * @param message information message
   */
  public static void showStatusMessage(final Project project, final String message) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (project.isOpen()) {
          StatusBar.Info.set(message, project);
        }
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
    Arrays.sort(files, new Comparator<FilePath>() {
      public int compare(FilePath o1, FilePath o2) {
        return sign * o1.getPath().compareTo(o2.getPath());
      }
    });
    return files;
  }

  /**
   * @param e ActionEvent object
   * @return <code>VirtualFile</code> available in the current context.
   *         Returns not <code>null</code> if and only if exectly one file is available.
   */
  @Nullable
  public static VirtualFile getOneVirtualFile(AnActionEvent e) {
    VirtualFile[] files = getVirtualFiles(e);
    return (files.length != 1) ? null : files[0];
  }

  /**
   * @param e ActionEvent object
   * @return <code>VirtualFile</code>s available in the current context.
   *         Returns empty array if there are no available files.
   */
  public static VirtualFile[] getVirtualFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    return (files == null) ? VirtualFile.EMPTY_ARRAY : files;
  }

  /**
   * Collects all files which are located in the passed directory.
   *
   * @throws IllegalArgumentException if <code>dir</code> isn't a directory.
   */
  public static void collectFiles(VirtualFile dir, List files, boolean recursive, boolean addDirectories) {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(VcsBundle.message("exception.text.file.should.be.directory", dir.getPresentableUrl()));
    }

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (!child.isDirectory() && (fileTypeManager == null || fileTypeManager.getFileTypeByFile(child) != FileTypes.UNKNOWN)) {
        files.add(child);
      }
      else if (recursive && child.isDirectory()) {
        if (addDirectories) {
          files.add(child);
        }
        collectFiles(child, files, recursive, false);
      }
    }
  }

  public static boolean runVcsProcessWithProgress(final VcsRunnable runnable, String progressTitle, boolean canBeCanceled, Project project)
    throws VcsException {
    final Ref<VcsException> ex = new Ref<VcsException>();
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          runnable.run();
        }
        catch (VcsException e) {
          ex.set(e);
        }
      }
    }, progressTitle, canBeCanceled, project);
    if (!ex.isNull()) {
      throw ex.get();
    }
    return result;
  }

  public static VirtualFile waitForTheFile(final String path) {
    final VirtualFile[] file = new VirtualFile[1];
    final Application app = ApplicationManager.getApplication();
    Runnable action = new Runnable() {
      public void run() {
        app.runWriteAction(new Runnable() {
          public void run() {
            file[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          }
        });
      }
    };

    if (app.isDispatchThread()) {
      action.run();
    }
    else {
      app.invokeAndWait(action, ModalityState.defaultModalityState());
    }

    return file[0];
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
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\'}) is <code>"c:\\my_directory\\//"</code>,
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\','/'}) is <code>"c:\my_directory"</code>.
   *         Actually this method can be used to normalize file names to chop trailing separator chars.
   */
  public static String chopTrailingChars(String source, char[] chars) {
    StringBuffer sb = new StringBuffer(source);
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

  public static VirtualFile[] paths2VFiles(String[] paths) {
    VirtualFile[] files = new VirtualFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      files[i] = getVirtualFile(paths[i]);
    }

    return files;
  }
}
