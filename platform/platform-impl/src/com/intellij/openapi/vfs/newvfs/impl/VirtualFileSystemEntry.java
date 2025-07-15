// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.core.CoreBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.InvalidVirtualFileAccessException.getInvalidationReason;
import static com.intellij.util.SystemProperties.getBooleanProperty;

public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = {};

  /**
   * true: use new (recursive) implementation of {@link #computePath(String, String)},
   * false: use legacy (iterative) implementation
   */
  private static final boolean USE_RECURSIVE_PATH_COMPUTE = getBooleanProperty("VirtualFileSystemEntry.USE_RECURSIVE_PATH_COMPUTE", true);

  /**
   * If true -- {@link #isValid()} return false for 'alien' vfiles (vfiles created in previous VFS session).
   * If false -- {@link #isValid()} throw AssertionError for 'alien' files (as all other method do)
   *
   * @see #isValid() comments for details
   */
  private static final boolean TREAT_ALIEN_FILES_AS_INVALID_INSTEAD_OF_CODE_BUG = getBooleanProperty(
    "VirtualFileSystemEntry.TREAT_ALIEN_FILES_AS_INVALID_INSTEAD_OF_CODE_BUG", true
  );

  @ApiStatus.Internal
  static final class VfsDataFlags {
    static final int IS_WRITABLE_FLAG = 0x0100_0000;
    static final int IS_HIDDEN_FLAG = 0x0200_0000;
    static final int IS_OFFLINE = 0x0400_0000;
    /** {@code true} if the line separator for this file was detected to be equal to {@link com.intellij.util.LineSeparator#getSystemLineSeparator()}. */
    static final int SYSTEM_LINE_SEPARATOR_DETECTED = 0x0800_0000; // applicable only to non-directory files
    /** The case-sensitivity of the directory children is known, so the value of {@link #CHILDREN_CASE_SENSITIVE} is actual. */
    static final int CHILDREN_CASE_SENSITIVITY_CACHED = SYSTEM_LINE_SEPARATOR_DETECTED; // applicable only to directories
    private static final int DIRTY_FLAG = 0x1000_0000;
    /** This file is a symlink. */
    static final int IS_SYMLINK_FLAG = 0x2000_0000;
    /** This file is not a symlink, but there's a symlink somewhere up among the parents. */
    static final int STRICT_PARENT_HAS_SYMLINK_FLAG = 0x4000_0000;
    /** This directory contains case-sensitive files. I.e. files "readme.txt" and "README.TXT" it can contain would be treated as different. */
    static final int CHILDREN_CASE_SENSITIVE = 0x8000_0000;     // applicable only to directories
    static final int IS_SPECIAL_FLAG = CHILDREN_CASE_SENSITIVE; // applicable only to non-directory files
  }

  static final int ALL_FLAGS_MASK =
    VfsDataFlags.IS_WRITABLE_FLAG |
    VfsDataFlags.IS_HIDDEN_FLAG |
    VfsDataFlags.IS_OFFLINE |
    VfsDataFlags.SYSTEM_LINE_SEPARATOR_DETECTED |
    VfsDataFlags.DIRTY_FLAG |
    VfsDataFlags.IS_SYMLINK_FLAG |
    VfsDataFlags.STRICT_PARENT_HAS_SYMLINK_FLAG |
    VfsDataFlags.CHILDREN_CASE_SENSITIVE;

  @MagicConstant(flagsFromClass = VfsDataFlags.class)
  @interface Flags {
  }

  private volatile @NotNull("except `NULL_VIRTUAL_FILE`") VfsData.Segment mySegment;
  private volatile VirtualDirectoryImpl myParent;
  final int myId;
  private volatile CachedFileType myFileType;

  static {
    assert ~ALL_FLAGS_MASK == LocalTimeCounter.TIME_MASK : "ALL_FLAGS_MASK and MOD_COUNTER_MASK must combined into full int32";
  }

  VirtualFileSystemEntry(int id, @NotNull VfsData.Segment segment, @Nullable VirtualDirectoryImpl parent) {
    mySegment = segment;
    myId = id;
    myParent = parent;
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive but got: " + id);
    }
  }

  private VirtualFileSystemEntry() {
    // an exception to instantiate the special singleton `NULL_VIRTUAL_FILE`
    //noinspection ConstantConditions
    mySegment = null;
    myParent = null;
    myId = -42;
  }

  @NotNull
  VfsData getVfsData() {
    VfsData data = mySegment.owningVfsData;
    PersistentFSImpl owningPersistentFS = data.owningPersistentFS();
    if (!owningPersistentFS.isOwnData(data)) {
      //PersistentFSImpl re-creates VfsData on (re-)connect
      throw new AssertionError("'Alien' file object: was created before PersistentFS (re-)connected " +
                               "(id=" + myId + ", parent=" + myParent + "), " +
                               "owningData: " + data + ", pFS: " + owningPersistentFS);
    }
    return data;
  }

  PersistentFSImpl owningPersistentFS() {
    return getVfsData().owningPersistentFS();
  }

  @NotNull
  VfsData.Segment getSegment() {
    VfsData.Segment segment = mySegment;
    if (segment.replacement != null) {
      segment = updateSegmentAndParent(segment);
    }
    return segment;
  }

  private @NotNull VfsData.Segment updateSegmentAndParent(@NotNull VfsData.Segment segment) {
    while (segment.replacement != null) {
      segment = segment.replacement;
    }
    VirtualDirectoryImpl changedParent = segment.owningVfsData.getChangedParent(myId);
    if (changedParent != null) {
      myParent = changedParent;
    }
    mySegment = segment;
    return segment;
  }

  void registerLink(@NotNull VirtualFileSystem fs) {
    if (fs instanceof LocalFileSystemImpl && isSymlink() && isValid()) {
      ((LocalFileSystemImpl)fs).symlinkUpdated(myId, myParent, getNameSequence(), getPath(), getCanonicalPath());
    }
  }

  void updateLinkStatus(@NotNull VirtualFileSystemEntry parent) {
    setFlagInt(VfsDataFlags.STRICT_PARENT_HAS_SYMLINK_FLAG, parent.thisOrParentHaveSymlink());
    registerLink(getFileSystem());
  }

  @Override
  public @NotNull String getName() {
    return getNameSequence().toString();
  }

  @Override
  public @NotNull CharSequence getNameSequence() {
    PersistentFSImpl pfs = owningPersistentFS();
    if (pfs == null) {
      return "<FS-is-disposed>";//shutdown-safe
    }
    return pfs.getName(myId);
  }

  public final int getNameId() {
    return owningPersistentFS().peer().getNameIdByFileId(myId);
  }

  @Override
  public VirtualDirectoryImpl getParent() {
    VfsData.Segment segment = mySegment;
    if (segment.replacement != null) {
      updateSegmentAndParent(segment);
    }
    return myParent;
  }

  @Override
  public boolean isDirty() {
    return getFlagInt(VfsDataFlags.DIRTY_FLAG) && !getFlagInt(VfsDataFlags.IS_OFFLINE);
  }

  @Override
  public boolean isOffline() {
    for (VirtualFileSystemEntry v = this; v != null; v = v.getParent()) {
      if (v.getFlagInt(VfsDataFlags.IS_OFFLINE)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setOffline(boolean offline) {
    boolean wasOffline = isOffline();
    setFlagInt(VfsDataFlags.IS_OFFLINE, offline);
    if (wasOffline && !isOffline()) {
      markDirtyRecursively();
    }
  }

  @Override
  public long getModificationStamp() {
    return isValid() ? getSegment().getModificationStamp(myId) : -1;
  }

  public void setModificationStamp(long modificationStamp) {
    getSegment().setModificationStamp(myId, modificationStamp);
  }

  boolean getFlagInt(@Flags int mask) {
    return getSegment().getFlag(myId, mask);
  }

  void setFlagInt(@Flags int mask, boolean value) {
    getSegment().setFlag(myId, mask, value);
  }

  @Override
  public void markClean() {
    setFlagInt(VfsDataFlags.DIRTY_FLAG, false);
  }

  @Override
  public void markDirty() {
    if (!isDirty()) {
      markDirtyInternal();
      VirtualFileSystemEntry parent = getParent();
      if (parent != null) parent.markDirty();
    }
  }

  void markDirtyInternal() {
    setFlagInt(VfsDataFlags.DIRTY_FLAG, true);
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  private @NotNull String computePath(@NotNull String protocol,
                                      @NotNull String protoSeparator) {
    if (USE_RECURSIVE_PATH_COMPUTE) {
      return computePathRecursively(
        this,
        protocol, protoSeparator,
        /* requiredBufferSize: */ 0
      ).toString();
    }
    else {
      return computePathIteratively(protocol, protoSeparator);
    }
  }

  /**
   * Recursive implementation of {@link #computePathIteratively(String, String)}: avoids allocating ArrayList, and uses
   * StringBuilder instead of plain char[] -- StringBuilder uses byte[] inside, which may be faster than explicit char[]
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull StringBuilder computePathRecursively(@NotNull VirtualFile file,
                                                              @NotNull String protocol,
                                                              @NotNull String protoSeparator,
                                                              int requiredBufferSize) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      String rootPath = file.getPath();
      return new StringBuilder(
        protocol.length() + protoSeparator.length() + rootPath.length() + requiredBufferSize
      )
        .append(protocol)
        .append(protoSeparator)
        .append(rootPath);//FSRoot.getPath() must end with '/'
    }

    String fileName = file.getName();

    StringBuilder pathBuilder = computePathRecursively(
      parent,
      protocol, protoSeparator,
      requiredBufferSize + fileName.length() + 1
    );

    pathBuilder.append(fileName);
    if (requiredBufferSize > 0) { // requiredBufferSize=0 is the top calling frame, don't need trailing '/'
      pathBuilder.append('/');
    }
    return pathBuilder;
  }

  /**
   * Legacy (iterative) implementation of {@link #computePath(String, String)}: builds the path into a char[], allocates
   * temporary ArrayList as stack
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public @NotNull String computePathIteratively(@NotNull String protocol,
                                                @NotNull String protoSeparator) {
    int length = 0;
    List<CharSequence> names = new ArrayList<>();
    VirtualFileSystemEntry v = this;
    for (; ; ) {
      VirtualDirectoryImpl parent = v.getParent();
      if (parent == null) {
        break;
      }
      CharSequence name = v.getNameSequence();
      if (length != 0) length++;
      length += name.length();
      names.add(name);
      v = parent;
    }
    int protocolLength = protocol.length();
    String rootPath = v.getPath();
    int rootPathLength = rootPath.length();
    length += protocolLength + protoSeparator.length() + rootPathLength;
    char[] path = new char[length];
    CharArrayUtil.getChars(protocol, path, 0);
    CharArrayUtil.getChars(protoSeparator, path, protocolLength);
    int o = protocolLength + protoSeparator.length();
    CharArrayUtil.getChars(rootPath, path, o, rootPathLength);
    o += rootPathLength;
    for (int i = names.size() - 1; i >= 1; i--) {
      CharSequence name = names.get(i);
      int nameLength = name.length();
      CharArrayUtil.getChars(name, path, o, nameLength);
      o += nameLength;
      path[o++] = '/';
    }
    if (!names.isEmpty()) {
      CharSequence name = names.get(0);
      CharArrayUtil.getChars(name, path, o);
    }
    return new String(path);
  }

  @Override
  public @NotNull String getUrl() {
    return computePath(getFileSystem().getProtocol(), "://");
  }

  @Override
  public @NotNull String getPath() {
    return computePath("", "");
  }

  @Override
  public void delete(Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    owningPersistentFS().deleteFile(requestor, this);
  }

  @Override
  public void rename(Object requestor, @NotNull @NonNls String newName) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (getName().equals(newName)) return;
    validateName(newName);
    owningPersistentFS().renameFile(requestor, this, newName);
  }

  @Override
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
    validateName(name);
    return owningPersistentFS().createChildFile(requestor, this, name);
  }

  @Override
  public boolean isWritable() {
    return getFlagInt(VfsDataFlags.IS_WRITABLE_FLAG);
  }

  @Override
  public void setWritable(boolean writable) throws IOException {
    owningPersistentFS().setWritable(this, writable);
  }

  @Override
  public long getTimeStamp() {
    return owningPersistentFS().getTimeStamp(this);
  }

  @Override
  public void setTimeStamp(long time) throws IOException {
    owningPersistentFS().setTimeStamp(this, time);
  }

  @Override
  public long getLength() {
    return owningPersistentFS().getLength(this);
  }

  @Override
  public @NotNull VirtualFile copy(Object requestor, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(CoreBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this, () -> owningPersistentFS().copyFile(requestor, this, newParent, copyName));
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      owningPersistentFS().moveFile(requestor, this, newParent);
      return this;
    });
  }

  @Override
  public int getId() {
    return myId;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof VirtualFileSystemEntry && myId == ((VirtualFileSystemEntry)o).myId;
  }

  @Override
  public int hashCode() {
    return myId;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    validateName(name);
    return owningPersistentFS().createChildDirectory(requestor, this, name);
  }

  private void validateName(@NotNull String name) throws IOException {
    if (!getFileSystem().isValidName(name)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", name));
    }
  }

  @Override
  public final boolean exists() {
    return getVfsData().isFileValid(myId);
  }

  @Override
  public final boolean isValid() {
    if (!TREAT_ALIEN_FILES_AS_INVALID_INSTEAD_OF_CODE_BUG) {
      return exists();
    }
    else {
      //RC: logically, isValid() == exists()
      //    The complication arises about the behavior in case of 'alien' files -- i.e. files that were created in previous VFS
      //    session. General policy is that such files must not exist: if VFS is reconnected, _all_ the VirtualFiles from
      //    previous session must be thrown out and must not be used _in any way_. By default, we consider _any_ use of such
      //    'alien' files a code bug, so we throw AssertionError if a VFile from a previous VFS epoch is used _in any way_
      //    (see getVfsData() for details).
      //
      //    Unfortunately, reality always strikes back against our best hopes: there are some cases, mainly in older junit3-4
      //    tests, there VirtualFiles _leaked_ from one test to another, with VFS re-connected in between -- which leads to
      //    flaky 'Alien file object' assertions failing the tests. So we're forced to compromise our integrity: isValid()
      //    is the only method that _doesn't_ throw the AssertionError for alien files, but returns false instead.
      //    In other words: we now consider an 'alien' file as 'invalid' file, instead of a primordial sin.

      VfsData data = mySegment.owningVfsData;
      PersistentFSImpl owningPersistentFS = data.owningPersistentFS();
      if (!owningPersistentFS.isOwnData(data)) {
        Logger.getInstance(VirtualFileSystemEntry.class).warn(
          "'Alien' file object: was created before PersistentFS (re-)connected (id=" + myId + ", parent=" + myParent + ")"
        );
        return false;
      }
      return data.isFileValid(myId);
    }
  }

  @Override
  public @NonNls String toString() {
    VfsData owningVfsData = getSegment().owningVfsData;
    //don't use .owningPersistentFS() since it throws assertion if pFS not own current segment anymore,
    // but here we want to return some string always:
    PersistentFSImpl persistentFs = owningVfsData.owningPersistentFS();
    if (!persistentFs.isOwnData(owningVfsData)) {
      //PersistentFSImpl re-creates VfsData on (re-)connect
      return "'Alien' file object: was created before PersistentFS (re-)connected " +
             "(id=" + myId + ", parent=" + myParent + ")";
    }

    if (exists()) {
      return getUrl();
    }

    String reason = getInvalidationReason(this);
    return getUrl() + " (invalid" + (reason == null ? "" : ", reason: " + reason) + ")";
  }

  public void setNewName(@NotNull String newName) {
    if (!getFileSystem().isValidName(newName)) {
      throw new IllegalArgumentException(CoreBundle.message("file.invalid.name.error", newName));
    }

    PersistentFSImpl pfs = owningPersistentFS();

    VirtualDirectoryImpl parent = getParent();
    //children are sorted by name: child position must change after its name has changed
    parent.removeChild(this);
    pfs.peer().setName(myId, newName);
    parent.addChild(this);

    pfs.incStructuralModificationCount();
  }

  public void setParent(@NotNull VirtualFile _newParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualDirectoryImpl newParent = (VirtualDirectoryImpl)_newParent;
    VirtualDirectoryImpl oldParent = getParent();

    //Both oldParent.myData & newParent.myData locks must be acquired here -- to prevent
    // FileAlreadyCreatedException in VirtualDirectoryImpl.createChildImpl()/VfsData$Segment.initFileData()
    // if called concurrently
    VirtualDirectoryImpl.runUnderAllLocksAcquired(
      () -> {
        oldParent.removeChild(this);

        getSegment().changeParent(myId, newParent);
        newParent.addChild(this);
        return (Void)null;
      },
      oldParent, newParent
    );
    updateLinkStatus(newParent);

    owningPersistentFS().incStructuralModificationCount();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  private static final class DebugInvalidation {
    private static final Logger LOG = Logger.getInstance(VirtualFileSystemEntry.class);
    private static final boolean DEBUG = LOG.isDebugEnabled();
  }

  @ApiStatus.Internal
  public void invalidate(@NotNull Object source, @NotNull Object reason) {
    getVfsData().invalidateFile(myId);
    appendInvalidationReason(source, reason);
  }

  @ApiStatus.Internal
  public void appendInvalidationReason(@NotNull Object source, @NotNull Object reason) {
    if (DebugInvalidation.DEBUG && !ApplicationManagerEx.isInStressTest()) {
      InvalidVirtualFileAccessException.appendInvalidationReason(this, source + ": " + reason);
    }
  }

  @Override
  public @NotNull Charset getCharset() {
    return isCharsetSet() ? super.getCharset() : computeCharset();
  }

  private @NotNull Charset computeCharset() {
    Charset charset;
    if (isDirectory()) {
      Charset configured = EncodingManager.getInstance().getEncoding(this, true);
      charset = configured == null ? Charset.defaultCharset() : configured;
      setCharset(charset);
    }
    else {
      FileType fileType = getFileType();
      if (isCharsetSet()) {
        // file type detection may have cached the charset, no need to re-detect
        return super.getCharset();
      }
      try {
        byte[] content = VfsUtilCore.loadBytes(this);
        if (isCharsetSet()) {
          // loadBytes() may have cached the charset (see VirtualFileImpl.contentsToByteArray(boolean))
          return super.getCharset();
        }
        charset = LoadTextUtil.detectCharsetAndSetBOM(this, content, fileType);
      }
      catch (IOException e) {
        return super.getCharset();
      }
    }
    return charset;
  }

  @Override
  public @NotNull String getPresentableName() {
    if (UISettings.getInstance().getHideKnownExtensionInTabs() && !isDirectory()) {
      String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.isEmpty() ? getName() : nameWithoutExtension;
    }
    return getName();
  }

  @Override
  public boolean is(@NotNull VFileProperty property) {
    if (property == VFileProperty.SPECIAL) return !isDirectory() && isSpecial();
    if (property == VFileProperty.HIDDEN) return getFlagInt(VfsDataFlags.IS_HIDDEN_FLAG);
    if (property == VFileProperty.SYMLINK) return isSymlink();
    throw new IllegalArgumentException("unknown property: " + property);
  }

  /**
   * @return true, if this file is symlink
   */
  private boolean isSymlink() {
    return getFlagInt(VfsDataFlags.IS_SYMLINK_FLAG);
  }

  /**
   * @return true, if this file is "special"
   */
  private boolean isSpecial() {
    return !isDirectory() && getFlagInt(VfsDataFlags.IS_SPECIAL_FLAG);
  }

  /**
   * @return true, if this file is a symlink or there is a symlink parent
   */
  @ApiStatus.Internal
  public boolean thisOrParentHaveSymlink() {
    return isSymlink() || getFlagInt(VfsDataFlags.STRICT_PARENT_HAS_SYMLINK_FLAG);
  }

  @ApiStatus.Internal
  public void setWritableFlag(boolean value) {
    setFlagInt(VfsDataFlags.IS_WRITABLE_FLAG, value);
  }

  @ApiStatus.Internal
  public void setHiddenFlag(boolean value) {
    setFlagInt(VfsDataFlags.IS_HIDDEN_FLAG, value);
  }

  @Override
  public String getCanonicalPath() {
    if (thisOrParentHaveSymlink()) {
      if (isSymlink()) {
        return owningPersistentFS().resolveSymLink(this);
      }
      VirtualFileSystemEntry parent = getParent();
      if (parent != null) {
        return parent.getCanonicalPath() + "/" + getName();
      }
      return getName();
    }
    return getPath();
  }

  @Override
  public NewVirtualFile getCanonicalFile() {
    if (thisOrParentHaveSymlink()) {
      String path = getCanonicalPath();
      return path != null ? (NewVirtualFile)getFileSystem().findFileByPath(path) : null;
    }
    return this;
  }

  @Override
  public boolean isRecursiveOrCircularSymlink() {
    if (!isSymlink()) return false;

    NewVirtualFile resolved = getCanonicalFile();
    // invalid symlink
    if (resolved == null) return false;
    // if it's recursive
    if (VfsUtilCore.isAncestor(resolved, this, false)) return true;

    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFileSystemEntry p = getParent(); p != null; p = p.getParent()) {
      // when the file has no symlinks up the hierarchy, it's not circular
      if (!p.thisOrParentHaveSymlink()) return false;
      if (p.isSymlink()) {
        VirtualFile parentResolved = p.getCanonicalFile();
        if (resolved.equals(parentResolved)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public final @NotNull FileType getFileType() {
    CachedFileType cache = myFileType;
    FileType type = cache == null ? null : cache.getUpToDateOrNull();
    if (type == null) {
      type = super.getFileType();
      myFileType = CachedFileType.forType(type);
    }
    return type;
  }

  static final VirtualFileSystemEntry NULL_VIRTUAL_FILE =
    new VirtualFileSystemEntry() {
      @Override
      public String toString() {
        return "NULL";
      }

      @Override
      public @NotNull NewVirtualFileSystem getFileSystem() {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable NewVirtualFile findChild(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable NewVirtualFile refreshAndFindChild(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable NewVirtualFile findChildIfCached(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull Collection<VirtualFile> getCachedChildren() {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull Iterable<VirtualFile> iterInDbChildren() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isDirectory() {
        throw new UnsupportedOperationException();
      }

      @Override
      public VirtualFile[] getChildren() {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull InputStream getInputStream() {
        throw new UnsupportedOperationException();
      }
    };
}
