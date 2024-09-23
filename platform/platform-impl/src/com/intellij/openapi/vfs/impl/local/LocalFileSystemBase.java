// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.core.CoreBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SlowOperations;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** @deprecated do not use directly, access via {@link LocalFileSystem#getInstance} */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public abstract class LocalFileSystemBase extends LocalFileSystem {
  private static final ExtensionPointName<LocalFileOperationsHandler> FILE_OPERATIONS_HANDLER_EP_NAME =
    ExtensionPointName.create("com.intellij.vfs.local.fileOperationsHandler");

  protected static final Logger LOG = Logger.getInstance(LocalFileSystemBase.class);

  private final List<LocalFileOperationsHandler> myHandlers = new ArrayList<>();

  public LocalFileSystemBase() { }

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
    var path = file.getPath();
    if (path.length() == 2 && SystemInfo.isWindows && OSAgnosticPathUtil.startsWithWindowsDrive(path)) {
      // makes 'C:' resolve to a root directory of the drive C:, not the current directory on that drive
      path += '/';
    }
    return path;
  }

  @Override
  public @Nullable Path getNioPath(@NotNull VirtualFile file) {
    return file.getFileSystem() == this ? Path.of(toIoPath(file)) : null;
  }

  private Path convertToNioFileAndCheck(VirtualFile file, boolean assertSlowOp) throws NoSuchFileException {
    if (assertSlowOp) { // remove condition when writes are moved to BGT
      SlowOperations.assertSlowOperationsAreAllowed();
    }
    if (SystemInfo.isUnix && file.is(VFileProperty.SPECIAL)) { // avoid opening FIFO files
      throw new NoSuchFileException(file.getPath(), null, "Not a file");
    }
    var path = getNioPath(file);
    if (path == null) throw new NoSuchFileException(file.getPath());
    return path;
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    return getAttributes(file) != null;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    var attributes = getAttributes(file);
    return attributes != null ? attributes.length : DEFAULT_LENGTH;
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    var attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    var attributes = getAttributes(file);
    return attributes != null && attributes.isDirectory();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    var attributes = getAttributes(file);
    return attributes != null && attributes.isWritable();
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    var attributes = getAttributes(file);
    return attributes != null && attributes.isSymLink();
  }

  @Override
  public @Nullable String resolveSymLink(@NotNull VirtualFile file) {
    try {
      return convertToNioFileAndCheck(file, false).toRealPath().toString();
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile dir) {
    var nioFile = Path.of(toIoPath(dir));
    return NioFiles.list(nioFile).stream()
      .map(NioFiles::getFileName)
      .toArray(String[]::new);
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
      var file = Path.of(path);
      if (!file.isAbsolute() && !(SystemInfo.isWindows && path.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path))) {
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
    refreshFiles(ContainerUtil.mapNotNull(files, this::refreshAndFindFileByIoFile), async, recursive, onFinish);
  }

  @Override
  public void refreshNioFiles(@NotNull Iterable<? extends Path> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    refreshFiles(ContainerUtil.mapNotNull(files, this::refreshAndFindFileByNioFile), async, recursive, onFinish);
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

  private Iterable<LocalFileOperationsHandler> handlers() {
    return ContainerUtil.concat(FILE_OPERATIONS_HANDLER_EP_NAME.getIterable(), myHandlers);
  }

  private boolean auxDelete(VirtualFile file) throws IOException {
    for (var handler : handlers()) {
      if (handler.delete(file)) return true;
    }
    return false;
  }

  private boolean auxMove(VirtualFile file, VirtualFile toDir) throws IOException {
    for (var handler : handlers()) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  private boolean auxCopy(VirtualFile file, VirtualFile toDir, String copyName) throws IOException {
    for (var handler : handlers()) {
      if (handler.copy(file, toDir, copyName) != null) return true;
    }
    return false;
  }

  private boolean auxRename(VirtualFile file, String newName) throws IOException {
    for (var handler : handlers()) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  private boolean auxCreateFile(VirtualFile dir, String name) throws IOException {
    for (var handler : handlers()) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  private boolean auxCreateDirectory(VirtualFile dir, String name) throws IOException {
    for (var handler : handlers()) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  private void auxNotifyCompleted(ThrowableConsumer<LocalFileOperationsHandler, IOException> consumer) {
    for (var handler : handlers()) {
      handler.afterDone(consumer);
    }
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    if (!isValidName(name)) {
      throw new IOException(CoreBundle.message("directory.invalid.name.error", name));
    }
    if (!parent.exists() || !parent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", parent.getPath()));
    }
    if (parent.findChild(name) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", parent.getPath() + "/" + name));
    }

    if (!auxCreateDirectory(parent, name)) {
      var nioFile = convertToNioFileAndCheck(parent, false).resolve(name);
      NioFiles.createDirectories(nioFile);
    }

    auxNotifyCompleted(handler -> handler.createDirectory(parent, name));

    return new FakeVirtualFile(parent, name);
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    if (!isValidName(name)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", name));
    }
    if (!parent.exists() || !parent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", parent.getPath()));
    }

    if (!auxCreateFile(parent, name)) {
      var nioFile = convertToNioFileAndCheck(parent, false).resolve(name);
      var existing = parent.findChild(name);
      NioFiles.createIfNotExists(nioFile);
      if (existing != null) {
        // Wow, I/O created the file successfully even though it already existed in VFS. Maybe we got dir case sensitivity wrong?
        var knownCS = parent.isCaseSensitive();
        var actualCS = FileSystemUtil.readParentCaseSensitivity(new File(existing.getPath()));
        if ((actualCS == FileAttributes.CaseSensitivity.SENSITIVE) != knownCS) {
          // we need to update case sensitivity
          var event = VirtualDirectoryImpl.generateCaseSensitivityChangedEvent(parent, actualCS);
          if (event != null) {
            RefreshQueue.getInstance().processEvents(false, List.of(event));
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
      var nioFile = convertToNioFileAndCheck(file, false);
      NioFiles.deleteRecursively(nioFile);
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
    var path = convertToNioFileAndCheck(file, true);
    return new BufferedInputStream(Files.newInputStream(path));
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    var nioFile = convertToNioFileAndCheck(file, true);
    var len = file.getLength();
    if (FileUtilRt.isTooLarge(len)) throw new FileTooBigException(file.getPath());
    if (len < 0) throw new IOException("Invalid file length: " + len + ", " + file);
    return Files.readAllBytes(nioFile);
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException {
    var path = convertToNioFileAndCheck(file, false);
    var stream = !SafeWriteRequestor.shouldUseSafeWrite(requestor) ? Files.newOutputStream(path) :
                 requestor instanceof LargeFileWriteRequestor ? new PreemptiveSafeFileOutputStream(path) :
                 new SafeFileOutputStream(path);
    return new BufferedOutputStream(stream) {
      @Override
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0 && Files.exists(path)) {
          Files.setLastModifiedTime(path, FileTime.fromMillis(timeStamp));
        }
      }
    };
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    var name = file.getName();
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

    if (!auxMove(file, newParent)) {
      var nioFile = convertToNioFileAndCheck(file, false);
      var nioTarget = convertToNioFileAndCheck(newParent, false).resolve(nioFile.getFileName());
      try {
        Files.move(nioFile, nioTarget, StandardCopyOption.ATOMIC_MOVE);
      }
      catch (AtomicMoveNotSupportedException e) {
        Files.move(nioFile, nioTarget);
      }
    }

    auxNotifyCompleted(handler -> handler.move(file, newParent));
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    if (!isValidName(newName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", newName));
    }
    if (!file.exists()) {
      throw new IOException(IdeCoreBundle.message("vfs.file.not.exist.error", file.getPath()));
    }
    var parent = file.getParent();
    if (parent == null) {
      throw new IOException(CoreBundle.message("cannot.rename.root.directory", file.getPath()));
    }
    var sameName = !file.isCaseSensitive() && newName.equalsIgnoreCase(file.getName());
    if (!sameName && parent.findChild(newName) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", parent.getPath() + '/' + newName));
    }

    if (!auxRename(file, newName)) {
      var nioFile = convertToNioFileAndCheck(file, false);
      var nioTarget = nioFile.resolveSibling(newName);
      try {
        Files.move(nioFile, nioTarget, StandardCopyOption.ATOMIC_MOVE);
      }
      catch (AtomicMoveNotSupportedException e) {
        Files.move(nioFile, nioTarget);
      }
    }

    auxNotifyCompleted(handler -> handler.rename(file, newName));
  }

  @Override
  public @NotNull VirtualFile copyFile(
    Object requestor,
    @NotNull VirtualFile file,
    @NotNull VirtualFile newParent,
    @NotNull String newName
  ) throws IOException {
    if (!isValidName(newName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", newName));
    }
    if (!file.exists()) {
      throw new IOException(IdeCoreBundle.message("vfs.file.not.exist.error", file.getPath()));
    }
    if (!newParent.exists() || !newParent.isDirectory()) {
      throw new IOException(IdeCoreBundle.message("vfs.target.not.directory.error", newParent.getPath()));
    }
    if (newParent.findChild(newName) != null) {
      throw new IOException(IdeCoreBundle.message("vfs.target.already.exists.error", newParent.getPath() + "/" + newName));
    }

    if (!auxCopy(file, newParent, newName)) {
      var nioFile = convertToNioFileAndCheck(file, false);
      var nioTarget = convertToNioFileAndCheck(newParent, false).resolve(newName);
      NioFiles.copyRecursively(nioFile, nioTarget);
    }

    auxNotifyCompleted(handler -> handler.copy(file, newParent, newName));

    return new FakeVirtualFile(newParent, newName);
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException {
    var nioFile = convertToNioFileAndCheck(file, false);
    Files.setLastModifiedTime(nioFile, FileTime.fromMillis(timeStamp));
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    var nioFile = convertToNioFileAndCheck(file, false);
    NioFiles.setReadOnly(nioFile, !writableFlag);
  }

  @Override
  protected @NotNull String extractRootPath(@NotNull String normalizedPath) {
    var rootPath = FileUtil.extractRootPath(normalizedPath);
    return rootPath != null ? rootPath : "";
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
    var parent = file.getParent();
    if (parent == null || parent.isCaseSensitive()) {
      return super.getCanonicallyCasedName(file);
    }

    var originalFileName = file.getName();
    var t = LOG.isTraceEnabled() ? System.nanoTime() : 0;
    try {
      if (SystemInfo.isWindows) {
        return convertToNioFileAndCheck(file, false).toRealPath(LinkOption.NOFOLLOW_LINKS).getFileName().toString();
      }
      // `Path#toRealPath` resolves the whole path starting from the root, but only the last component is necessary
      try (var stream = Files.newDirectoryStream(convertToNioFileAndCheck(parent, false))) {
        for (var path : stream) {
          var name = path.getFileName().toString();
          if (
            originalFileName.equalsIgnoreCase(name) ||
            Normalizer.isNormalized(originalFileName, Normalizer.Form.NFC) &&
            originalFileName.equalsIgnoreCase(Normalizer.normalize(name, Normalizer.Form.NFC))
          ) {
            return name;
          }
        }
      }
      return originalFileName;
    }
    catch (IOException | InvalidPathException e) {
      LOG.trace(e);
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
    try {
      var nioFile = Path.of(toIoPath(file));
      var nioAttributes = Files.readAttributes(nioFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return FileAttributes.fromNio(nioFile, nioAttributes);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  public void refresh(boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }

  @Override
  public boolean hasChildren(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      return true;  // assume roots always have children
    }
    try (var stream = Files.newDirectoryStream(Paths.get(file.getPath()))) {
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
}
