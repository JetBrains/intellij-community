// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.core.CoreBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringFactory;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;

public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];

  static final PersistentFS ourPersistence = PersistentFS.getInstance();

  @ApiStatus.Internal
  static class VfsDataFlags {
    static final int IS_WRITABLE_FLAG = 0x0100_0000;
    static final int IS_HIDDEN_FLAG = 0x0200_0000;
    static final int CHILDREN_CACHED = 0x0800_0000; // makes sense for directory only
    /**
     * true if the line separator for this file was detected to be equal to {@link com.intellij.util.LineSeparator#getSystemLineSeparator()}
     */
    static final int SYSTEM_LINE_SEPARATOR_DETECTED = CHILDREN_CACHED; // makes sense for non-directory file only
    static final int IS_SYMLINK_FLAG = 0x2000_0000;
    static final int IS_SPECIAL_FLAG = 0x8000_0000; // makes sense for non-directory file only
    /**
     * true if this directory contains case-sensitive files. I.e. files "readme.txt" and "README.TXT" it can contain would be treated as different files.
     */
    static final int CHILDREN_CASE_SENSITIVE = IS_SPECIAL_FLAG; // makes sense for directory only
    private static final int INDEXED_FLAG = 0x0400_0000; // makes sense for non-directory only
    /**
     * the case-sensitivity of this directory children is known, so the flag CHILDREN_CASE_SENSITIVE is actual
     */
    static final int CHILDREN_CASE_SENSITIVITY_CACHED = INDEXED_FLAG; // makes sense for directory only
    private static final int DIRTY_FLAG = 0x1000_0000;
    private static final int PARENT_HAS_SYMLINK_FLAG = 0x4000_0000;
  }
  static final int ALL_FLAGS_MASK =
    VfsDataFlags.DIRTY_FLAG | VfsDataFlags.IS_SYMLINK_FLAG |
    VfsDataFlags.PARENT_HAS_SYMLINK_FLAG | VfsDataFlags.IS_SPECIAL_FLAG | VfsDataFlags.IS_WRITABLE_FLAG | VfsDataFlags.IS_HIDDEN_FLAG | VfsDataFlags.INDEXED_FLAG | VfsDataFlags.CHILDREN_CACHED |
    VfsDataFlags.CHILDREN_CASE_SENSITIVE | VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED;

  @MagicConstant(flagsFromClass = VfsDataFlags.class)
  @interface Flags {}

  @NotNull // except NULL_VIRTUAL_FILE
  private volatile VfsData.Segment mySegment;
  private volatile VirtualDirectoryImpl myParent;
  final int myId;
  private volatile CachedFileType myFileType;

  static {
    //noinspection ConstantConditions
    assert (~ALL_FLAGS_MASK) == LocalTimeCounter.TIME_MASK;
  }

  VirtualFileSystemEntry(int id, @NotNull VfsData.Segment segment, @Nullable VirtualDirectoryImpl parent) {
    mySegment = segment;
    myId = id;
    myParent = parent;
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive but got: "+id);
    }
  }

  // for NULL_FILE
  private VirtualFileSystemEntry() {
    // although in general mySegment is always @NotNull, in this case we made an exception to be able to instantiate special singleton NULL_VIRTUAL_FILE
    //noinspection ConstantConditions
    mySegment = null;
    myParent = null;
    myId = -42;
  }

  @NotNull VfsData getVfsData() {
    return getSegment().vfsData;
  }

  VfsData.@NotNull Segment getSegment() {
    VfsData.Segment segment = mySegment;
    if (segment.replacement != null) {
      segment = updateSegmentAndParent(segment);
    }
    return segment;
  }

  private VfsData.Segment updateSegmentAndParent(VfsData.Segment segment) {
    while (segment.replacement != null) {
      segment = segment.replacement;
    }
    VirtualDirectoryImpl changedParent = segment.vfsData.getChangedParent(myId);
    if (changedParent != null) {
      myParent = changedParent;
    }
    mySegment = segment;
    return segment;
  }

  void registerLink(@NotNull VirtualFileSystem fs) {
    if (fs instanceof LocalFileSystemImpl && is(VFileProperty.SYMLINK) && isValid()) {
      ((LocalFileSystemImpl)fs).symlinkUpdated(myId, myParent, getNameSequence(), getPath(), getCanonicalPath());
    }
  }

  void updateLinkStatus(boolean isSymlink, @NotNull VirtualFileSystemEntry parent) {
    setFlagInt(VfsDataFlags.PARENT_HAS_SYMLINK_FLAG, isSymlink || parent.parentHasSymlink());
    registerLink(getFileSystem());
  }

  @Override
  @NotNull
  public String getName() {
    return getNameSequence().toString();
  }

  @NotNull
  @Override
  public CharSequence getNameSequence() {
    return FileNameCache.getVFileName(getNameId());
  }

  public final int getNameId() {
    return getSegment().getNameId(myId);
  }

  @Override
  public VirtualDirectoryImpl getParent() {
    VfsData.Segment segment = mySegment;
    if (segment.replacement != null) {
      updateSegmentAndParent(segment);
    }
    return myParent;
  }

  /**
   * @return true if it's a symlink or there is a symlink parent
   */
  public boolean parentHasSymlink() {
    return getFlagInt(VfsDataFlags.PARENT_HAS_SYMLINK_FLAG);
  }

  @Override
  public boolean isDirty() {
    return getFlagInt(VfsDataFlags.DIRTY_FLAG);
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

  public boolean isFileIndexed() {
    return !isDirectory() && getFlagInt(VfsDataFlags.INDEXED_FLAG);
  }

  public void setFileIndexed(boolean indexed) {
    if (!isDirectory()) {
      setFlagInt(VfsDataFlags.INDEXED_FLAG, indexed);
    }
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

  protected char @NotNull [] appendPathOnFileSystem(int accumulatedPathLength, int @NotNull [] positionRef) {
    CharSequence name = getNameSequence();

    char[] chars = getParent().appendPathOnFileSystem(accumulatedPathLength + 1 + name.length(), positionRef);
    int i = positionRef[0];
    chars[i] = '/';
    positionRef[0] = copyString(chars, i + 1, name);

    return chars;
  }

  private static int copyString(char @NotNull [] chars, int pos, @NotNull CharSequence s) {
    int length = s.length();
    CharArrayUtil.getChars(s, chars, 0, pos, length);
    return pos + length;
  }

  @Override
  @NotNull
  public String getUrl() {
    String protocol = getFileSystem().getProtocol();
    int prefixLen = protocol.length() + "://".length();
    char[] chars = appendPathOnFileSystem(prefixLen, new int[]{prefixLen});
    copyString(chars, copyString(chars, 0, protocol), "://");
    return StringFactory.createShared(chars);
  }

  @Override
  @NotNull
  public String getPath() {
    return StringFactory.createShared(appendPathOnFileSystem(0, new int[]{0}));
  }

  @Override
  public void delete(final Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    ourPersistence.deleteFile(requestor, this);
  }

  @Override
  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (getName().equals(newName)) return;
    validateName(newName);
    ourPersistence.renameFile(requestor, this, newName);
  }

  @Override
  @NotNull
  public VirtualFile createChildData(final Object requestor, @NotNull final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildFile(requestor, this, name);
  }

  @Override
  public boolean isWritable() {
    return getFlagInt(VfsDataFlags.IS_WRITABLE_FLAG);
  }

  @Override
  public void setWritable(boolean writable) throws IOException {
    ourPersistence.setWritable(this, writable);
  }

  @Override
  public long getTimeStamp() {
    return ourPersistence.getTimeStamp(this);
  }

  @Override
  public void setTimeStamp(final long time) throws IOException {
    ourPersistence.setTimeStamp(this, time);
  }

  @Override
  public long getLength() {
    return ourPersistence.getLength(this);
  }

  @NotNull
  @Override
  public VirtualFile copy(final Object requestor, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(CoreBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this, () -> ourPersistence.copyFile(requestor, this, newParent, copyName));
  }

  @Override
  public void move(final Object requestor, @NotNull final VirtualFile newParent) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      ourPersistence.moveFile(requestor, this, newParent);
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
  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildDirectory(requestor, this, name);
  }

  private void validateName(@NotNull String name) throws IOException {
    if (!getFileSystem().isValidName(name)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", name));
    }
  }

  @Override
  public boolean exists() {
    return getVfsData().isFileValid(myId);
  }

  @Override
  public boolean isValid() {
    return exists();
  }

  @Override
  @NonNls
  public String toString() {
    if (isValid()) {
      return getUrl();
    }
    String reason = getUserData(DebugInvalidation.INVALIDATION_REASON);
    return getUrl() + " (invalid" + (reason == null ? "" : ", reason: "+reason) + ")";
  }

  public void setNewName(@NotNull String newName) {
    if (!getFileSystem().isValidName(newName)) {
      throw new IllegalArgumentException(CoreBundle.message("file.invalid.name.error", newName));
    }

    VirtualDirectoryImpl parent = getParent();
    parent.removeChild(this);
    getSegment().setNameId(myId, FileNameCache.storeName(newName));
    parent.addChild(this);
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  public void setParent(@NotNull VirtualFile newParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualDirectoryImpl parent = getParent();
    parent.removeChild(this);

    VirtualDirectoryImpl directory = (VirtualDirectoryImpl)newParent;
    getSegment().changeParent(myId, directory);
    directory.addChild(this);
    updateLinkStatus(is(VFileProperty.SYMLINK), directory);
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  private static class DebugInvalidation {
    private static final boolean DEBUG = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();
    private static final Key<String> INVALIDATION_REASON = Key.create("INVALIDATION_REASON");
  }

  @ApiStatus.Internal
  public void invalidate(@NotNull Object source, @NotNull Object reason) {
    getVfsData().invalidateFile(myId);
    appendInvalidationReason(source, reason);
  }

  @ApiStatus.Internal
  public void appendInvalidationReason(@NotNull Object source, @NotNull Object reason) {
    if (DebugInvalidation.DEBUG && !ApplicationInfoImpl.isInStressTest()) {
      String oldReason = getUserData(DebugInvalidation.INVALIDATION_REASON);
      String newReason = source + ": " + reason;
      putUserData(DebugInvalidation.INVALIDATION_REASON, oldReason == null ? newReason : oldReason + "; " + newReason);
    }
  }

  @NotNull
  @Override
  public Charset getCharset() {
    return isCharsetSet() ? super.getCharset() : computeCharset();
  }

  @NotNull
  private Charset computeCharset() {
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
        final byte[] content = VfsUtilCore.loadBytes(this);
        charset = LoadTextUtil.detectCharsetAndSetBOM(this, content, fileType);
      }
      catch (IOException e) {
        return super.getCharset();
      }
    }
    return charset;
  }

  @Override
  public String getPresentableName() {
    if (UISettings.getInstance().getHideKnownExtensionInTabs() && !isDirectory()) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.isEmpty() ? getName() : nameWithoutExtension;
    }
    return getName();
  }

  @Override
  public boolean is(@NotNull VFileProperty property) {
    if (property == VFileProperty.SPECIAL) return !isDirectory() && getFlagInt(VfsDataFlags.IS_SPECIAL_FLAG);
    if (property == VFileProperty.HIDDEN) return getFlagInt(VfsDataFlags.IS_HIDDEN_FLAG);
    if (property == VFileProperty.SYMLINK) return getFlagInt(VfsDataFlags.IS_SYMLINK_FLAG);
    throw new IllegalArgumentException("unknown property: "+property);
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
    if (parentHasSymlink()) {
      if (is(VFileProperty.SYMLINK)) {
        return ourPersistence.resolveSymLink(this);
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
    if (parentHasSymlink()) {
      final String path = getCanonicalPath();
      return path != null ? (NewVirtualFile)getFileSystem().findFileByPath(path) : null;
    }
    return this;
  }

  @Override
  public boolean isRecursiveOrCircularSymlink() {
    if (!is(VFileProperty.SYMLINK)) return false;
    NewVirtualFile resolved = getCanonicalFile();
    // invalid symlink
    if (resolved == null) return false;
    // if it's recursive
    if (VfsUtilCore.isAncestor(resolved, this, false)) return true;

    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFileSystemEntry p = getParent(); p != null ; p = p.getParent()) {
      // optimization: when the file has no symlinks up the hierarchy, it's not circular
      if (!p.parentHasSymlink()) return false;
      if (p.is(VFileProperty.SYMLINK)) {
        VirtualFile parentResolved = p.getCanonicalFile();
        if (resolved.equals(parentResolved)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    CachedFileType cache = myFileType;
    FileType type = cache == null ? null : cache.getUpToDateOrNull();
    if (type == null) {
      type = super.getFileType();
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        myFileType = CachedFileType.forType(type);
      }
    }
    return type;
  }

  static final VirtualFileSystemEntry NULL_VIRTUAL_FILE =
    new VirtualFileSystemEntry() {
      @Override
      public String toString() {
        return "NULL";
      }

      @NotNull
      @Override
      public NewVirtualFileSystem getFileSystem() {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public NewVirtualFile findChild(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public NewVirtualFile refreshAndFindChild(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public NewVirtualFile findChildIfCached(@NotNull String name) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      @Override
      public Collection<VirtualFile> getCachedChildren() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      @Override
      public Iterable<VirtualFile> iterInDbChildren() {
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

      @NotNull
      @Override
      public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
        throw new UnsupportedOperationException();
      }

      @Override
      public InputStream getInputStream() {
        throw new UnsupportedOperationException();
      }
    };
}
