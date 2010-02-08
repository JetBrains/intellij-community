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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.io.SafeFileOutputStream;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Dmitry Avdeev
 */
public abstract class LocalFileSystemBase extends LocalFileSystem {

  protected static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");
  private final List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();

  @Nullable
  public VirtualFile findFileByPath(@NotNull String path) {
    /*
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }
    */

    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPath(canonicalPath);
  }

  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPathIfCached(canonicalPath);
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.refreshAndFindFileByPath(canonicalPath);
  }

  public VirtualFile findFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile findFileByIoFile(final IFile file) {
    String path = file.getPath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  protected static String getVfsCanonicalPath(@NotNull String path) {
    if (path.length() == 0) {
      try {
        return new File("").getCanonicalPath();
      }
      catch (IOException e) {
        return path;
      }
    }

    if (SystemInfo.isWindows) {
      if (path.startsWith("//") || path.startsWith("\\\\")) {
        return path;
      }

      if (path.charAt(0) == '/') path = path.substring(1); //hack over new File(path).toUrl().getFile()
      if (path.contains("~")) {
        try {
          return new File(path.replace('/', File.separatorChar)).getCanonicalPath().replace(File.separatorChar, '/');
        }
        catch (IOException e) {
          return null;
        }
      }
    }
    else {
      if (!path.startsWith("/")) {
        path = new File(path).getAbsolutePath();
      }
    }


    return path.replace(File.separatorChar, '/');
  }

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
  protected static File convertToIOFile(VirtualFile file) {
    String path = file.getPath();
    if (path.endsWith(":") && path.length() == 2 && (SystemInfo.isWindows || SystemInfo.isOS2)) {
      path += "/"; // Make 'c:' resolve to a root directory for drive c:, not the current directory on that drive
    }

    return new File(path);
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) return true;
    return convertToIOFile(fileOrDirectory).exists();
  }

  public long getLength(final VirtualFile file) {
    return convertToIOFile(file).length();
  }

  public long getTimeStamp(final VirtualFile file) {
    return convertToIOFile(file).lastModified();
  }

  public boolean isDirectory(final VirtualFile file) {
    return convertToIOFile(file).isDirectory();
  }

  public boolean isWritable(final VirtualFile file) {
    return convertToIOFile(file).canWrite();
  }

  public String[] list(final VirtualFile file) {
    if (file.getParent() == null) {
      final File[] roots = File.listRoots();
      if (roots.length == 1 && roots[0].getName().length() == 0) {
        return roots[0].list();
      }
      if ("".equals(file.getName())) {
        // return drive letter names for the 'fake' root on windows
        final String[] names = new String[roots.length];
        for (int i = 0; i < names.length; i++) {
          String name = roots[i].getPath();
          if (name.endsWith(File.separator)) {
            name = name.substring(0, name.length() - File.separator.length());
          }
          names[i] = name;
        }
        return names;
      }
    }
    final String[] names = convertToIOFile(file).list();
    return names != null ? names : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  public String normalize(final String path) {
    return getVfsCanonicalPath(path);
  }

  public VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    String path = ioFile.getPath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  public void refreshIoFiles(Iterable<File> files) {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    Application app = ApplicationManager.getApplication();
    boolean fireCommonRefreshSession = app.isDispatchThread() || app.isWriteAccessAllowed();
    if (fireCommonRefreshSession) manager.fireBeforeRefreshStart(false);

    try {
      List<VirtualFile> filesToRefresh = new ArrayList<VirtualFile>();

      for (File file : files) {
        final VirtualFile virtualFile = refreshAndFindFileByIoFile(file);
        if (virtualFile != null) {
          filesToRefresh.add(virtualFile);
        }
      }

      RefreshQueue.getInstance().refresh(false, false, null, VfsUtil.toVirtualFileArray(filesToRefresh));
    }
    finally {
      if (fireCommonRefreshSession) manager.fireAfterRefreshFinish(false);
    }
  }

  public void refreshFiles(Iterable<VirtualFile> files) {
    refreshFiles(files, false, false);
  }

  protected static void refreshFiles(final Iterable<VirtualFile> files, final boolean recursive, final boolean async) {
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      list.add(file);
    }

    RefreshQueue.getInstance().refresh(async, recursive, null, VfsUtil.toVirtualFileArray(list));
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    return virtualFile.contentsToByteArray();
  }

  public long physicalLength(final VirtualFile virtualFile) {
    return virtualFile.getLength();
  }

  public void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (myHandlers.contains(handler)) {
      LOG.error("Handler " + handler + " already registered.");
    }
    myHandlers.add(handler);
  }

  public void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (!myHandlers.remove(handler)) {
      LOG.error("Handler" + handler + " haven't been registered or already unregistered.");
    }
  }

  public boolean processCachedFilesInSubtree(final VirtualFile file, Processor<VirtualFile> processor) {
    if (file.getFileSystem() != this) return true;

    return processFile((NewVirtualFile)file, processor);
  }

  private static boolean processFile(NewVirtualFile file, Processor<VirtualFile> processor) {
    if (!processor.process(file)) return false;
    if (file.isDirectory()) {
      for (final VirtualFile child : file.getCachedChildren()) {
        if (!processFile((NewVirtualFile)child, processor)) return false;
      }
    }
    return true;
  }

  private boolean auxDelete(VirtualFile file) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.delete(file)) return true;
    }

    return false;
  }

  private boolean auxMove(VirtualFile file, VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  private void auxNotifyCompleted(final ThrowableConsumer<LocalFileOperationsHandler, IOException> consumer) {
    for (LocalFileOperationsHandler handler : myHandlers) {
      handler.afterDone(consumer);
    }
  }

  @Nullable
  private File auxCopy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      final File copy = handler.copy(file, toDir, copyName);
      if (copy != null) return copy;
    }
    return null;
  }

  private boolean auxRename(VirtualFile file, String newName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  private boolean auxCreateFile(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  private boolean auxCreateDirectory(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  private static void delete(File physicalFile) throws IOException {
    File[] list = physicalFile.listFiles();
    if (list != null) {
      for (File aList : list) {
        delete(aList);
      }
    }
    if (!physicalFile.delete()) {
      throw new IOException(VfsBundle.message("file.delete.error", physicalFile.getPath()));
    }
  }

  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    final File ioDir = new File(convertToIOFile(parent), dir);
    final boolean succ = auxCreateDirectory(parent, dir) || ioDir.mkdirs();
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createDirectory(parent, dir);
      }
    });
    if (!succ) {
      throw new IOException("Failed to create directory: " + ioDir.getPath());
    }

    return new FakeVirtualFile(parent, dir);
  }

  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
    final File ioFile = new File(convertToIOFile(parent), file);
    final boolean succ = auxCreateFile(parent, file) || FileUtil.createIfDoesntExist(ioFile);
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createFile(parent, file);
      }
    });
    if (!succ) {
      throw new IOException("Failed to create child file at " + ioFile.getPath());
    }

    return new FakeVirtualFile(parent, file);
  }

  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    if (!auxDelete(file)) {
      delete(convertToIOFile(file));
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.delete(file);
      }
    });
  }

  public boolean isCaseSensitive() {
    return SystemInfo.isFileSystemCaseSensitive;
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws FileNotFoundException {
    return new BufferedInputStream(new FileInputStream(convertToIOFile(file)));
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    return FileUtil.loadFileBytes(convertToIOFile(file));
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws FileNotFoundException {
    final File ioFile = convertToIOFile(file);
    final OutputStream stream = shallUseSafeStream(requestor, ioFile) ? new SafeFileOutputStream(ioFile) : new FileOutputStream(ioFile);
    return new BufferedOutputStream(stream) {
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0) {
          ioFile.setLastModified(timeStamp);
        }
      }
    };
  }

  private static boolean shallUseSafeStream(Object requestor, File file) {
    return requestor instanceof SafeWriteRequestor && FileUtil.canCallCanExecute() && !FileUtil.canExecute(file);
  }

  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    if (!auxMove(file, newParent)) {
      final File ioFrom = convertToIOFile(file);
      final File ioParent = convertToIOFile(newParent);
      ioFrom.renameTo(new File(ioParent, file.getName()));
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.move(file, newParent);
      }
    });
  }

  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
    if (!file.exists()) {
      throw new IOException("File to move does not exist: " + file.getPath());
    }

    final VirtualFile parent = file.getParent();
    assert parent != null;

    if (!auxRename(file, newName)) {
      if (!convertToIOFile(file).renameTo(new File(convertToIOFile(parent), newName))) {
        throw new IOException("Destination already exists: " + parent.getPath() + "/" + newName);
      }
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.rename(file, newName);
      }
    });
  }

  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final VirtualFile newParent, @NotNull final String copyName)
    throws IOException {
    File physicalCopy = auxCopy(vFile, newParent, copyName);

    try {
      if (physicalCopy == null) {
        File physicalFile = convertToIOFile(vFile);

        File newPhysicalParent = convertToIOFile(newParent);
        physicalCopy = new File(newPhysicalParent, copyName);

        try {
          if (physicalFile.isDirectory()) {
            FileUtil.copyDir(physicalFile, physicalCopy);
          }
          else {
            FileUtil.copy(physicalFile, physicalCopy);
          }
        }
        catch (IOException e) {
          FileUtil.delete(physicalCopy);
          throw e;
        }
      }
    } finally {
      auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
        public void consume(LocalFileOperationsHandler handler) throws IOException {
          handler.copy(vFile, newParent, copyName);
        }
      });
    }
    return new FakeVirtualFile(newParent, copyName);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) {
    convertToIOFile(file).setLastModified(modstamp);
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    FileUtil.setReadOnlyAttribute(file.getPath(), !writableFlag);
    final File ioFile = convertToIOFile(file);
    if (ioFile.canWrite() != writableFlag) {
      throw new IOException("Failed to change read-only flag for " + ioFile.getPath());
    }
  }

  protected String extractRootPath(@NotNull final String path) {
    if (path.length() == 0) {
      try {
        return extractRootPath(new File("").getCanonicalPath());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    if (SystemInfo.isWindows) {
      if (path.length() >= 2 && path.charAt(1) == ':') {
        // Drive letter
        return path.substring(0, 2).toUpperCase(Locale.US);
      }

      if (path.startsWith("//") || path.startsWith("\\\\")) {
        // UNC. Must skip exactly two path elements like [\\ServerName\ShareName]\pathOnShare\file.txt
        // Root path is in square brackets here.

        int slashCount = 0;
        int idx;
        for (idx = 2; idx < path.length() && slashCount < 2; idx++) {
          final char c = path.charAt(idx);
          if (c == '\\' || c == '/') {
            slashCount++;
            idx--;
          }
        }

        return path.substring(0, idx);
      }

      return "";
    }

    return path.startsWith("/") ? "/" : "";
  }

  public int getRank() {
    return 1;
  }

  public boolean markNewFilesAsDirty() {
    return true;
  }

  public String getCanonicallyCasedName(final VirtualFile file) {
    if (isCaseSensitive()) {
      return super.getCanonicallyCasedName(file);
    }

    try {
      return convertToIOFile(file).getCanonicalFile().getName();
    }
    catch (IOException e) {
      return file.getName();
    }
  }
}
