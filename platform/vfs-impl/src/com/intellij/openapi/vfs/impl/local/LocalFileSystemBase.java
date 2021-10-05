// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.core.CoreBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtilRt;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PreemptiveSafeFileOutputStream;
import com.intellij.util.io.SafeFileOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class LocalFileSystemBase extends LocalFileSystem {
  @ApiStatus.Internal
  public static final ExtensionPointName<PluggableLocalFileSystemContentLoader> PLUGGABLE_CONTENT_LOADER_EP_NAME =
    ExtensionPointName.create("com.intellij.vfs.local.pluggableContentLoader");
  protected static final Logger LOG = Logger.getInstance(LocalFileSystemBase.class);

  private final FileAttributes FAKE_ROOT_ATTRIBUTES =
    new FileAttributes(true, false, false, false, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, false,
                       isCaseSensitive() ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE);

  private final List<LocalFileOperationsHandler> myHandlers = new ArrayList<>();

  @Override
  public @Nullable VirtualFile findFileByPath(@NotNull String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  protected static @NotNull String toIoPath(@NotNull VirtualFile file) {
    String path = file.getPath();
    if (StringUtil.endsWithChar(path, ':') && path.length() == 2 && SystemInfo.isWindows) {
      // makes 'C:' resolve to a root directory of the drive C:, not the current directory on that drive
      path += '/';
    }
    return path;
  }

  private static @NotNull File convertToIOFile(@NotNull VirtualFile file) {
    return new File(toIoPath(file));
  }

  @Override
  public @Nullable Path getNioPath(@NotNull VirtualFile file) {
    return file.getFileSystem() == this ? Paths.get(toIoPath(file)) : null;
  }

  protected static @NotNull File convertToIOFileAndCheck(@NotNull VirtualFile file) throws FileNotFoundException {
    File ioFile = convertToIOFile(file);

    if (SystemInfo.isUnix && file.is(VFileProperty.SPECIAL)) { // avoid opening fifo files
      throw new FileNotFoundException("Not a file: " + ioFile + " (type=" + FileSystemUtil.getAttributes(ioFile) + ')');
    }

    return ioFile;
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    return getAttributes(file) != null;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.length : DEFAULT_LENGTH;
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null && attributes.isDirectory();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null && attributes.isWritable();
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null && attributes.isSymLink();
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    String result = FileSystemUtil.resolveSymLink(file.getPath());
    return result != null ? FileUtilRt.toSystemIndependentName(result) : null;
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    String[] names = myChildrenGetter.accessDiskWithCheckCanceled(convertToIOFile(file));
    return names == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : names;
  }

  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  protected @Nullable String normalize(@NotNull String path) {
    if (SystemInfoRt.isWindows) {
      if (path.length() > 1 && path.charAt(0) == '/' && path.charAt(1) != '/') {
        path = path.substring(1);  // hack around `new File(path).toURI().toURL().getFile()`
      }

      try {
        path = FileUtil.resolveShortWindowsName(path);
      }
      catch (IOException e) {
        return null;
      }
    }

    try {
      Path file = Path.of(path);
      if (!file.isAbsolute() && !(SystemInfo.isWindows && path.length() == 2 && path.charAt(1) == ':')) {
        path = file.toAbsolutePath().toString();
      }
    }
    catch (InvalidPathException | IOError e) {
      Logger.getInstance(getClass()).trace(e);
      return null;
    }

    return FileUtil.normalize(path);
  }

  @Override
  public void refreshIoFiles(@NotNull Iterable<? extends File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    List<VirtualFile> virtualFiles = ContainerUtil.mapNotNull(files, f1 -> refreshAndFindFileByIoFile(f1));
    refreshFiles(async, recursive, virtualFiles, onFinish);
  }

  @Override
  public void refreshNioFiles(@NotNull Iterable<? extends Path> files,
                              boolean async,
                              boolean recursive,
                              @Nullable Runnable onFinish) {
    List<VirtualFile> virtualFiles = ContainerUtil.mapNotNull(files, f1 -> refreshAndFindFileByNioFile(f1));
    refreshFiles(async, recursive, virtualFiles, onFinish);
  }

  private static void refreshFiles(boolean async,
                                   boolean recursive,
                                   List<? extends VirtualFile> virtualFiles,
                                   @Nullable Runnable onFinish) {
    VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    Application app = ApplicationManager.getApplication();
    boolean fireCommonRefreshSession = app.isDispatchThread() || app.isWriteAccessAllowed();
    if (fireCommonRefreshSession) manager.fireBeforeRefreshStart(false);

    try {
      RefreshQueue.getInstance().refresh(async, recursive, onFinish, virtualFiles);
    }
    finally {
      if (fireCommonRefreshSession) manager.fireAfterRefreshFinish(false);
    }
  }

  @Override
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    RefreshQueue.getInstance().refresh(async, recursive, onFinish, ContainerUtil.toCollection(files));
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    if (myHandlers.contains(handler)) {
      LOG.error("Handler " + handler + " already registered.");
    }
    myHandlers.add(handler);
  }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    if (!myHandlers.remove(handler)) {
      LOG.error("Handler " + handler + " haven't been registered or already unregistered.");
    }
  }

  private boolean auxDelete(@NotNull VirtualFile file) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.delete(file)) return true;
    }

    return false;
  }

  private boolean auxMove(@NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  private boolean auxCopy(@NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String copyName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      File copy = handler.copy(file, toDir, copyName);
      if (copy != null) return true;
    }
    return false;
  }

  private boolean auxRename(@NotNull VirtualFile file, @NotNull String newName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  private boolean auxCreateFile(@NotNull VirtualFile dir, @NotNull String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  private boolean auxCreateDirectory(@NotNull VirtualFile dir, @NotNull String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  private void auxNotifyCompleted(@NotNull ThrowableConsumer<LocalFileOperationsHandler, IOException> consumer) {
    for (LocalFileOperationsHandler handler : myHandlers) {
      handler.afterDone(consumer);
    }
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    if (!isValidName(dir)) {
      throw new IOException(CoreBundle.message("directory.invalid.name.error", dir));
    }

    if (!parent.exists() || !parent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", parent.getPath()));
    }
    if (parent.findChild(dir) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", parent.getPath() + "/" + dir));
    }

    File ioParent = convertToIOFile(parent);
    if (!ioParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("target.not.directory.error", ioParent.getPath()));
    }

    if (!auxCreateDirectory(parent, dir)) {
      File ioDir = new File(ioParent, dir);
      if (!(ioDir.mkdirs() || ioDir.isDirectory())) {
        throw new IOException(IdeCoreBundle.message("new.directory.failed.error", ioDir.getPath()));
      }
    }

    auxNotifyCompleted(handler -> handler.createDirectory(parent, dir));

    return new FakeVirtualFile(parent, dir);
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    if (!isValidName(name)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", name));
    }

    if (!parent.exists() || !parent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", parent.getPath()));
    }

    File ioParent = convertToIOFile(parent);
    if (!ioParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("target.not.directory.error", ioParent.getPath()));
    }

    if (!auxCreateFile(parent, name)) {
      File ioFile = new File(ioParent, name);
      VirtualFile existing = parent.findChild(name);
      boolean created = FileUtilRt.createIfNotExists(ioFile);
      if (!created) {
        if (existing != null) {
          throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", parent.getPath() + "/" + name));
        }

        throw new IOException(IdeCoreBundle.message("new.file.failed.error", ioFile.getPath()));
      }
      else if (existing != null) {
        // wow, IO created file successfully although it already existed in VFS. Maybe we got dir case sensitivity wrong?
        boolean oldCaseSensitive = parent.isCaseSensitive();
        FileAttributes.CaseSensitivity actualSensitivity = FileSystemUtil.readParentCaseSensitivity(new File(existing.getPath()));
        if ((actualSensitivity == FileAttributes.CaseSensitivity.SENSITIVE) != oldCaseSensitive) {
          // we need to update case sensitivity
          VFilePropertyChangeEvent event = VfsImplUtil.generateCaseSensitivityChangedEvent(parent, actualSensitivity);
          if (event != null) {
            RefreshQueue.getInstance().processSingleEvent(false, event);
          }
        }
      }
    }

    auxNotifyCompleted(handler -> handler.createFile(parent, name));

    return new FakeVirtualFile(parent, name);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    if (file.getParent() == null) {
      throw new IOException(IdeCoreBundle.message("cannot.delete.root.directory", file.getPath()));
    }

    if (!auxDelete(file)) {
      File ioFile = convertToIOFile(file);
      if (!FileUtil.delete(ioFile)) {
        throw new IOException(IdeCoreBundle.message("delete.failed.error", ioFile.getPath()));
      }
    }

    auxNotifyCompleted(handler -> handler.delete(file));
  }

  @Override
  public boolean isCaseSensitive() {
    return SystemInfo.isFileSystemCaseSensitive;
  }

  @Override
  public boolean isValidName(@NotNull String name) {
    return PathUtilRt.isValidFileName(name, false);
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    File ioFile = convertToIOFileAndCheck(file);

    for (PluggableLocalFileSystemContentLoader loader : PLUGGABLE_CONTENT_LOADER_EP_NAME.getExtensionList()) {
      InputStream is = loader.getInputStream(ioFile);
      if (is != null) {
        return is;
      }
    }

    return new BufferedInputStream(new FileInputStream(ioFile));
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    File ioFile = convertToIOFileAndCheck(file);

    for (PluggableLocalFileSystemContentLoader loader : PLUGGABLE_CONTENT_LOADER_EP_NAME.getExtensionList()) {
      byte[] bytes = loader.contentToByteArray(ioFile);
      if (bytes != null) {
        return bytes;
      }
    }

    long l = file.getLength();
    if (l >= FileUtilRt.LARGE_FOR_CONTENT_LOADING) throw new FileTooBigException(file.getPath());
    int length = (int)l;
    if (length < 0) throw new IOException("Invalid file length: " + length + ", " + file);
    return loadFileContent(ioFile, length);
  }

  protected static byte @NotNull [] loadFileContent(@NotNull File ioFile, int length) throws IOException {
    try (InputStream stream = new FileInputStream(ioFile)) {
      // io_util.c#readBytes allocates custom native stack buffer for io operation with malloc if io request > 8K
      // so let's do buffered requests with buffer size 8192 that will use stack allocated buffer
      return loadBytes(length <= 8192 ? stream : new BufferedInputStream(stream), length);
    }
  }

  private static byte @NotNull [] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    byte[] bytes = ArrayUtil.newByteArray(length);
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count < length) {
      // this may happen with encrypted files, see IDEA-143773
      return Arrays.copyOf(bytes, count);
    }
    return bytes;
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException {
    File ioFile = convertToIOFileAndCheck(file);
    OutputStream stream = !SafeWriteRequestor.shouldUseSafeWrite(requestor) ? new FileOutputStream(ioFile) :
                          Registry.is("ide.io.preemptive.safe.write") ? new PreemptiveSafeFileOutputStream(ioFile.toPath()) :
                          new SafeFileOutputStream(ioFile);
    return new BufferedOutputStream(stream) {
      @Override
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0 && ioFile.exists()) {
          if (!ioFile.setLastModified(timeStamp)) {
            LOG.warn("Failed: " + ioFile.getPath() + ", new:" + timeStamp + ", old:" + ioFile.lastModified());
          }
        }
      }
    };
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    String name = file.getName();

    if (!file.exists()) {
      throw new IOException(IdeCoreBundle.message("vfs.file.not.exist.error", file.getPath()));
    }
    if (file.getParent() == null) {
      throw new IOException(CoreBundle.message("cannot.rename.root.directory", file.getPath()));
    }
    if (!newParent.exists() || !newParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", newParent.getPath()));
    }
    if (newParent.findChild(name) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", newParent.getPath() + "/" + name));
    }

    File ioFile = convertToIOFile(file);
    if (FileSystemUtil.getAttributes(ioFile) == null) {
      throw new FileNotFoundException(IdeCoreBundle.message("file.not.exist.error", ioFile.getPath()));
    }
    File ioParent = convertToIOFile(newParent);
    if (!ioParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("target.not.directory.error", ioParent.getPath()));
    }
    File ioTarget = new File(ioParent, name);
    if (ioTarget.exists()) {
      throw new IOException(IdeCoreBundle.message("target.already.exists.error", ioTarget.getPath()));
    }

    if (!auxMove(file, newParent)) {
      if (!ioFile.renameTo(ioTarget)) {
        throw new IOException(IdeCoreBundle.message("move.failed.error", ioFile.getPath(), ioParent.getPath()));
      }
    }

    auxNotifyCompleted(handler -> handler.move(file, newParent));
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    if (!isValidName(newName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", newName));
    }

    boolean sameName = !file.isCaseSensitive() && newName.equalsIgnoreCase(file.getName());

    if (!file.exists()) {
      throw new IOException(IdeCoreBundle.message("vfs.file.not.exist.error", file.getPath()));
    }
    VirtualFile parent = file.getParent();
    if (parent == null) {
      throw new IOException(CoreBundle.message("cannot.rename.root.directory", file.getPath()));
    }
    if (!sameName && parent.findChild(newName) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", parent.getPath() + "/" + newName));
    }

    File ioFile = convertToIOFile(file);
    if (!ioFile.exists()) {
      throw new FileNotFoundException(IdeCoreBundle.message("file.not.exist.error", ioFile.getPath()));
    }
    File ioTarget = new File(convertToIOFile(parent), newName);
    if (!sameName && ioTarget.exists()) {
      throw new IOException(IdeCoreBundle.message("target.already.exists.error", ioTarget.getPath()));
    }

    if (!auxRename(file, newName)) {
      if (!FileUtil.rename(ioFile, newName)) {
        throw new IOException(IdeCoreBundle.message("rename.failed.error", ioFile.getPath(), newName));
      }
    }

    auxNotifyCompleted(handler -> handler.rename(file, newName));
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) throws IOException {
    if (!isValidName(copyName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", copyName));
    }

    if (!file.exists()) {
      throw new IOException(IdeCoreBundle.message("vfs.file.not.exist.error", file.getPath()));
    }
    if (!newParent.exists() || !newParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", newParent.getPath()));
    }
    if (newParent.findChild(copyName) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", newParent.getPath() + "/" + copyName));
    }

    FileAttributes attributes = getAttributes(file);
    if (attributes == null) {
      throw new FileNotFoundException(IdeCoreBundle.message("file.not.exist.error", file.getPath()));
    }
    if (attributes.isSpecial()) {
      throw new FileNotFoundException("Not a file: " + file);
    }
    File ioParent = convertToIOFile(newParent);
    if (!ioParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("target.not.directory.error", ioParent.getPath()));
    }
    File ioTarget = new File(ioParent, copyName);
    if (ioTarget.exists()) {
      throw new IOException(IdeCoreBundle.message("target.already.exists.error", ioTarget.getPath()));
    }

    if (!auxCopy(file, newParent, copyName)) {
      try {
        File ioFile = convertToIOFile(file);
        FileUtil.copyFileOrDir(ioFile, ioTarget, attributes.isDirectory());
      }
      catch (IOException e) {
        FileUtil.delete(ioTarget);
        throw e;
      }
    }

    auxNotifyCompleted(handler -> handler.copy(file, newParent, copyName));

    return new FakeVirtualFile(newParent, copyName);
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) {
    File ioFile = convertToIOFile(file);
    if (ioFile.exists() && !ioFile.setLastModified(timeStamp)) {
      LOG.warn("Failed: " + file.getPath() + ", new:" + timeStamp + ", old:" + ioFile.lastModified());
    }
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    String path = FileUtilRt.toSystemDependentName(file.getPath());
    FileUtil.setReadOnlyAttribute(path, !writableFlag);
    if (FileUtil.canWrite(path) != writableFlag) {
      throw new IOException("Failed to change read-only flag for " + path);
    }
  }

  @Override
  protected @NotNull String extractRootPath(@NotNull String normalizedPath) {
    String rootPath = FileUtil.extractRootPath(normalizedPath);
    return StringUtil.notNullize(rootPath);
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
  public @NotNull String getCanonicallyCasedName(@NotNull VirtualFile file) {
    if (file.getParent().isCaseSensitive()) {
      return super.getCanonicallyCasedName(file);
    }

    String originalFileName = file.getName();
    long t = LOG.isTraceEnabled() ? System.nanoTime() : 0;
    try {
      File ioFile = convertToIOFile(file);

      File canonicalFile = ioFile.getCanonicalFile();
      String canonicalFileName = canonicalFile.getName();
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
      File parentFile = ioFile.getParentFile();
      if (parentFile != null) {
        // I hope ls works fast on Unix
        String[] canonicalFileNames = parentFile.list();
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
    catch (IOException | InvalidPathException e) {
      return originalFileName;
    }
    finally {
      if (t != 0) {
        t = (System.nanoTime() - t) / 1000;
        LOG.trace("getCanonicallyCasedName(" + file + "): " + t + " mks");
      }
    }
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    String path = file.getPath();
    if (SystemInfo.isWindows && file.getParent() == null && path.startsWith("//")) {
      return FAKE_ROOT_ATTRIBUTES;  // UNC roots
    }
    return myAttrGetter.accessDiskWithCheckCanceled(file);
  }

  private final DiskQueryRelay<VirtualFile, FileAttributes> myAttrGetter = new DiskQueryRelay<>(LocalFileSystemBase::getAttributesWithCustomTimestamp);
  private final DiskQueryRelay<File, String[]> myChildrenGetter = new DiskQueryRelay<>(dir -> dir.list());

  @Override
  public void refresh(boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }

  @Override
  public boolean hasChildren(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      return true;  // assume roots always have children
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(file.getPath()))) {
      return stream.iterator().hasNext();  // make sure to not load all children
    }
    catch (DirectoryIteratorException e) {
      return false;  // a directory can't be iterated over
    }
    catch (InvalidPathException | IOException | SecurityException e) {
      return true;
    }
  }

  @TestOnly
  public void cleanupForNextTest() {
    FileDocumentManager.getInstance().saveAllDocuments();
    PersistentFS.getInstance().clearIdCache();
  }

  private static @Nullable FileAttributes getAttributesWithCustomTimestamp(@NotNull VirtualFile file) {
    final FileAttributes fs = FileSystemUtil.getAttributes(FileUtilRt.toSystemDependentName(file.getPath()));
    if (fs == null) return null;

    for (LocalFileSystemTimestampEvaluator provider : LocalFileSystemTimestampEvaluator.EP_NAME.getExtensionList()) {
      final Long custom = provider.getTimestamp(file);
      if (custom != null) {
        return new FileAttributes(fs.isDirectory(), fs.isSpecial(), fs.isSymLink(), fs.isHidden(), fs.length, custom, fs.isWritable(), fs.areChildrenCaseSensitive());
      }
    }

    return fs;
  }
}
