/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author max
 */
public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];

  static final PersistentFS ourPersistence = PersistentFS.getInstance();

  private static final Key<String> SYMLINK_TARGET = Key.create("local.vfs.symlink.target");

          static final int IS_WRITABLE_FLAG = 0x01000000;
          static final int IS_HIDDEN_FLAG =   0x02000000;
  private static final int INDEXED_FLAG =     0x04000000;
          static final int CHILDREN_CACHED =  0x08000000; // makes sense for directory only
  static final int SYSTEM_LINE_SEPARATOR_DETECTED = CHILDREN_CACHED; // makes sense for non-directory file only
  private static final int DIRTY_FLAG =       0x10000000;
          static final int IS_SYMLINK_FLAG =  0x20000000;
  private static final int HAS_SYMLINK_FLAG = 0x40000000;
          static final int IS_SPECIAL_FLAG =  0x80000000;

  static final int ALL_FLAGS_MASK =
    DIRTY_FLAG | IS_SYMLINK_FLAG | HAS_SYMLINK_FLAG | IS_SPECIAL_FLAG | IS_WRITABLE_FLAG | IS_HIDDEN_FLAG | INDEXED_FLAG | CHILDREN_CACHED;

  final VfsData.Segment mySegment;
  private final VirtualDirectoryImpl myParent;
  final int myId;

  static {
    //noinspection ConstantConditions
    assert (~ALL_FLAGS_MASK) == LocalTimeCounter.TIME_MASK;
  }

  VirtualFileSystemEntry(int id, @NotNull VfsData.Segment segment, @Nullable VirtualDirectoryImpl parent) {
    mySegment = segment;
    myId = id;
    myParent = parent;
  }

  void updateLinkStatus() {
    boolean isSymLink = is(VFileProperty.SYMLINK);
    if (isSymLink) {
      String target = getParent().getFileSystem().resolveSymLink(this);
      setLinkTarget(target != null ? FileUtil.toSystemIndependentName(target) : null);
    }
    setFlagInt(HAS_SYMLINK_FLAG, isSymLink || getParent().getFlagInt(HAS_SYMLINK_FLAG));
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
    return mySegment.getNameId(myId);
  }

  @Override
  public VirtualDirectoryImpl getParent() {
    VirtualDirectoryImpl changedParent = VfsData.getChangedParent(myId);
    return changedParent != null ? changedParent : myParent;
  }

  @Override
  public boolean isDirty() {
    return getFlagInt(DIRTY_FLAG);
  }

  @Override
  public long getModificationStamp() {
    return mySegment.getModificationStamp(myId);
  }

  public void setModificationStamp(long modificationStamp) {
    mySegment.setModificationStamp(myId, modificationStamp);
  }

  boolean getFlagInt(int mask) {
    return mySegment.getFlag(myId, mask);
  }

  void setFlagInt(int mask, boolean value) {
    mySegment.setFlag(myId, mask, value);
  }

  public boolean isFileIndexed() {
    return getFlagInt(INDEXED_FLAG);
  }

  public void setFileIndexed(boolean indexed) {
    setFlagInt(INDEXED_FLAG, indexed);
  }

  @Override
  public void markClean() {
    setFlagInt(DIRTY_FLAG, false);
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
    setFlagInt(DIRTY_FLAG, true);
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  @NotNull
  protected char[] appendPathOnFileSystem(int accumulatedPathLength, int[] positionRef) {
    CharSequence name = FileNameCache.getVFileName(mySegment.getNameId(myId));

    char[] chars = getParent().appendPathOnFileSystem(accumulatedPathLength + 1 + name.length(), positionRef);
    int i = positionRef[0];
    chars[i] = '/';
    positionRef[0] = copyString(chars, i + 1, name);

    return chars;
  }

  protected static int copyString(@NotNull char[] chars, int pos, @NotNull CharSequence s) {
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
    return getFlagInt(IS_WRITABLE_FLAG);
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

  @Override
  public VirtualFile copy(final Object requestor, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this, () -> ourPersistence.copyFile(requestor, this, newParent, copyName));
  }

  @Override
  public void move(final Object requestor, @NotNull final VirtualFile newParent) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      ourPersistence.moveFile(requestor, this, newParent);
      return this;
    });
  }

  @Override
  public int getId() {
    return VfsData.isFileValid(myId) ? myId : -myId;
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
      throw new IOException(VfsBundle.message("file.invalid.name.error", name));
    }
  }

  @Override
  public boolean exists() {
    return VfsData.isFileValid(myId);
  }

  @Override
  public boolean isValid() {
    return exists();
  }

  public String toString() {
    return getUrl();
  }

  public void setNewName(@NotNull String newName) {
    if (!getFileSystem().isValidName(newName)) {
      throw new IllegalArgumentException(VfsBundle.message("file.invalid.name.error", newName));
    }

    VirtualDirectoryImpl parent = getParent();
    parent.removeChild(this);
    mySegment.setNameId(myId, FileNameCache.storeName(newName));
    parent.addChild(this);
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  public void setParent(@NotNull VirtualFile newParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualDirectoryImpl parent = getParent();
    parent.removeChild(this);

    VirtualDirectoryImpl directory = (VirtualDirectoryImpl)newParent;
    VfsData.changeParent(myId, directory);
    directory.addChild(this);
    updateLinkStatus();
    ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    VfsData.invalidateFile(myId);
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
      try {
        final byte[] content = VfsUtilCore.loadBytes(this);
        charset = LoadTextUtil.detectCharsetAndSetBOM(this, content, getFileType());
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
    if (property == VFileProperty.SPECIAL) return getFlagInt(IS_SPECIAL_FLAG);
    if (property == VFileProperty.HIDDEN) return getFlagInt(IS_HIDDEN_FLAG);
    if (property == VFileProperty.SYMLINK) return getFlagInt(IS_SYMLINK_FLAG);
    return super.is(property);
  }

  public void updateProperty(@NotNull String property, boolean value) {
    if (property == PROP_WRITABLE) setFlagInt(IS_WRITABLE_FLAG, value);
    if (property == PROP_HIDDEN) setFlagInt(IS_HIDDEN_FLAG, value);
  }

  public void setLinkTarget(@Nullable String target) {
    putUserData(SYMLINK_TARGET, target);
  }

  @Override
  public String getCanonicalPath() {
    if (getFlagInt(HAS_SYMLINK_FLAG)) {
      if (is(VFileProperty.SYMLINK)) {
        return getUserData(SYMLINK_TARGET);
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
    if (getFlagInt(HAS_SYMLINK_FLAG)) {
      final String path = getCanonicalPath();
      return path != null ? (NewVirtualFile)getFileSystem().findFileByPath(path) : null;
    }
    return this;
  }
}