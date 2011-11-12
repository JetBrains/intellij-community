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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
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

  @Override
  @Nullable
  public VirtualFile findFileByPath(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return VfsImplUtil.findFileByPathIfCached(this, canonicalPath);
  }

  @Override
  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return VfsImplUtil.refreshAndFindFileByPath(this, canonicalPath);
  }

  @Override
  public VirtualFile findFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Override
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

  @NotNull
  protected static File convertToIOFile(@NotNull final VirtualFile file) {
    String path = file.getPath();
    if (path.endsWith(":") && path.length() == 2 && (SystemInfo.isWindows || SystemInfo.isOS2)) {
      path += "/"; // Make 'c:' resolve to a root directory for drive c:, not the current directory on that drive
    }

    return new File(path);
  }

  @NotNull
  private static File convertToIOFileAndCheck(@NotNull final VirtualFile file) throws FileNotFoundException {
    final File ioFile = convertToIOFile(file);

    if (ioFile.exists() && !ioFile.isFile()) {
      throw new FileNotFoundException("Not a file: " + ioFile);
    }

    return ioFile;
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    String path = fileOrDirectory.getPath();
    if (fileOrDirectory.getParent() == null && path.startsWith("//")) return true; // Windows UNC root like //unit-333
    if (StringUtil.isEmpty(path)) return true; // fake top dir for Windows
    return convertToIOFile(fileOrDirectory).exists();
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
    return convertToIOFile(file).length();
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    return convertToIOFile(file).lastModified();
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    return convertToIOFile(file).isDirectory();
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    return convertToIOFile(file).canWrite();
  }

  @Override
  public boolean isSymLink(@NotNull final VirtualFile file) {
    return FileSystemUtil.isSymLink(file.getPath());
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return FileSystemUtil.resolveSymLink(file.getPath());
  }

  @Override
  public boolean isSpecialFile(@NotNull final VirtualFile file) {
    final File ioFile = convertToIOFile(file);
    return ioFile.exists() && !ioFile.isFile() && !ioFile.isDirectory();
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    if (file.getParent() == null) {
      final File[] roots = File.listRoots();
      if (roots.length == 1 && roots[0].getName().isEmpty()) {
        return roots[0].list();
      }
      if (file.getName().isEmpty()) {
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

    if (isInvalidSymLink(file)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    final String[] names = convertToIOFile(file).list();
    return names != null ? names : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  protected static boolean isInvalidSymLink(@NotNull final VirtualFile file) {
    if (!file.isSymLink()) return false;
    final VirtualFile realFile = file.getRealFile();
    return realFile == null ||
           realFile == file ||
           FileUtil.isAncestor(convertToIOFile(realFile), convertToIOFile(file), true);
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  @Nullable
  public String normalize(final String path) {
    return getVfsCanonicalPath(path);
  }

  @Override
  public VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Override
  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    String path = ioFile.getPath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Override
  public void refreshIoFiles(Iterable<File> files) {
    refreshIoFiles(files, false, false, null);
  }

  @Override
  public void refreshIoFiles(Iterable<File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
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

      RefreshQueue.getInstance().refresh(async, recursive, onFinish, VfsUtil.toVirtualFileArray(filesToRefresh));
    }
    finally {
      if (fireCommonRefreshSession) manager.fireAfterRefreshFinish(false);
    }
  }

  @Override
  public void refreshFiles(Iterable<VirtualFile> files) {
    refreshFiles(files, false, false, null);
  }

  @Override
  public void refreshFiles(Iterable<VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      list.add(file);
    }

    RefreshQueue.getInstance().refresh(async, recursive, onFinish, VfsUtil.toVirtualFileArray(list));
  }

  @Override
  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    return virtualFile.contentsToByteArray();
  }

  @Override
  public long physicalLength(final VirtualFile virtualFile) {
    return virtualFile.getLength();
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (myHandlers.contains(handler)) {
      LOG.error("Handler " + handler + " already registered.");
    }
    myHandlers.add(handler);
  }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (!myHandlers.remove(handler)) {
      LOG.error("Handler" + handler + " haven't been registered or already unregistered.");
    }
  }

  @Override
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
    if (!FileSystemUtil.isSymLink(physicalFile)) {
      File[] list = physicalFile.listFiles();
      if (list != null) {
        for (File aList : list) {
          delete(aList);
        }
      }
    }
    if (!physicalFile.delete()) {
      throw new IOException(VfsBundle.message("file.delete.error", physicalFile.getPath()));
    }
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    final File ioDir = new File(convertToIOFile(parent), dir);
    final boolean succeed = auxCreateDirectory(parent, dir) || ioDir.mkdirs();
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      @Override
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createDirectory(parent, dir);
      }
    });
    if (!succeed) {
      throw new IOException("Failed to create directory: " + ioDir.getPath());
    }

    return new FakeVirtualFile(parent, dir);
  }

  @Override
  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
    final File ioFile = new File(convertToIOFile(parent), file);
    final boolean succeed = auxCreateFile(parent, file) || FileUtil.createIfDoesntExist(ioFile);
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      @Override
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createFile(parent, file);
      }
    });
    if (!succeed) {
      throw new IOException("Failed to create child file at " + ioFile.getPath());
    }

    return new FakeVirtualFile(parent, file);
  }

  @Override
  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    if (!auxDelete(file)) {
      delete(convertToIOFile(file));
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      @Override
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.delete(file);
      }
    });
  }

  @Override
  public boolean isCaseSensitive() {
    return SystemInfo.isFileSystemCaseSensitive;
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    return new BufferedInputStream(new FileInputStream(convertToIOFileAndCheck(file)));
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    final FileInputStream stream = new FileInputStream(convertToIOFileAndCheck(file));
    try {
      return FileUtil.loadBytes(stream, (int)file.getLength());
    }
    finally {
      stream.close();
    }
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    final File ioFile = convertToIOFileAndCheck(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final OutputStream stream = shallUseSafeStream(requestor, ioFile) ? new SafeFileOutputStream(ioFile, SystemInfo.isUnix) : new FileOutputStream(ioFile);
    return new BufferedOutputStream(stream) {
      @Override
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0 && ioFile.exists()) {
          if (!ioFile.setLastModified(timeStamp)) {
            LOG.warn("Failed: " + file.getPath() + ", new:" + timeStamp + ", old:" + ioFile.lastModified());
          }
        }
      }
    };
  }

  private static boolean shallUseSafeStream(final Object requestor, final File file) {
    return requestor instanceof SafeWriteRequestor && !FileUtil.isSymbolicLink(file);
  }

  @Override
  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    if (!auxMove(file, newParent)) {
      final File ioFrom = convertToIOFile(file);
      final File ioParent = convertToIOFile(newParent);
      if (!ioParent.isDirectory()) {
        throw new IOException("Target '" + ioParent + "' is not a directory");
      }
      if (!ioFrom.renameTo(new File(ioParent, file.getName()))) {
        throw new IOException("Move failed: '" + file.getPath() + "' to '" + newParent.getPath() +"'");
      }
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      @Override
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.move(file, newParent);
      }
    });
  }

  @Override
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
      @Override
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.rename(file, newName);
      }
    });
  }

  @Override
  public VirtualFile copyFile(final Object requestor,
                              @NotNull final VirtualFile vFile,
                              @NotNull final VirtualFile newParent,
                              @NotNull final String copyName) throws IOException {
    File physicalFile = convertToIOFile(vFile);
    if (physicalFile.exists() && !physicalFile.isFile() && !physicalFile.isDirectory()) {
      throw new FileNotFoundException("Not a file: " + physicalFile);
    }

    File physicalCopy = auxCopy(vFile, newParent, copyName);

    try {
      if (physicalCopy == null) {
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
    }
    finally {
      auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
        @Override
        public void consume(LocalFileOperationsHandler handler) throws IOException {
          handler.copy(vFile, newParent, copyName);
        }
      });
    }
    return new FakeVirtualFile(newParent, copyName);
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long timeStamp) {
    final File ioFile = convertToIOFile(file);
    if (ioFile.exists() && !ioFile.setLastModified(timeStamp)) {
      LOG.warn("Failed: " + file.getPath() + ", new:" + timeStamp + ", old:" + ioFile.lastModified());
    }
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    FileUtil.setReadOnlyAttribute(file.getPath(), !writableFlag);
    final File ioFile = convertToIOFile(file);
    if (ioFile.canWrite() != writableFlag) {
      throw new IOException("Failed to change read-only flag for " + ioFile.getPath());
    }
  }

  @Override
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

  @Override
  public int getRank() {
    return 1;
  }

  @Override
  public boolean markNewFilesAsDirty() {
    return true;
  }

  @Override
  public String getCanonicallyCasedName(final VirtualFile file) {
    if (isCaseSensitive()) {
      return super.getCanonicallyCasedName(file);
    }

    final String originalFileName = file.getName();
    try {
      final File ioFile = convertToIOFile(file);
      final File ioCanonicalFile = ioFile.getCanonicalFile();
      String canonicalFileName = ioCanonicalFile.getName();
      if (!SystemInfo.isUnix) {
        return canonicalFileName;
      }
      // linux & mac support symbolic links
      // unfortunately canonical file resolves sym links
      // so its name may differ from name of origin file
      //
      // Here FS is case sensitive, so let's check that original and
      // canonical file names are equal if we ignore name case
      if (canonicalFileName.compareToIgnoreCase(originalFileName) == 0) {
        // p.s. this should cover most cases related to not symbolic links
        return canonicalFileName;
      }

      // Ok, names are not equal. Let's try to find corresponding file name
      // among original file parent directory
      final File parentFile = ioFile.getParentFile();
      if (parentFile != null) {
        // I hope ls works fast on Unix
        final String[] canonicalFileNames = parentFile.list();
        if (canonicalFileNames != null) {
          for (String name : canonicalFileNames) {
            // if names are equals
            if (name.compareToIgnoreCase(originalFileName) == 0) {
              return name;
            }
          }
        }
      }
      // No luck. So ein mist!
      // Ok, garbage in, garbage out. We may return original or canonical name
      // no difference. Let's return canonical name just to preserve previous
      // behaviour of this code.
      return canonicalFileName;
    }
    catch (IOException e) {
      return originalFileName;
    }
  }

  @Override
  public void refresh(final boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }
}
