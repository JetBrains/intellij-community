/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author max
 */
public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];

  @NonNls protected static final String FS_ROOT_FAKE_NAME = "";
  protected static final PersistentFS ourPersistence = PersistentFS.getInstance();

  private static final Key<String> SYMLINK_TARGET = Key.create("local.vfs.symlink.target");

  private static final int DIRTY_FLAG =       0x10000000;
  private static final int IS_SYMLINK_FLAG =  0x20000000;
  private static final int HAS_SYMLINK_FLAG = 0x40000000;
  private static final int IS_SPECIAL_FLAG =  0x80000000;
  private static final int IS_WRITABLE_FLAG = 0x01000000;
  private static final int IS_HIDDEN_FLAG =   0x02000000;
  private static final int INDEXED_FLAG =     0x04000000;
          static final int CHILDREN_CACHED =  0x08000000;

  private static final int ALL_FLAGS_MASK =
    DIRTY_FLAG | IS_SYMLINK_FLAG | HAS_SYMLINK_FLAG | IS_SPECIAL_FLAG | IS_WRITABLE_FLAG | IS_HIDDEN_FLAG | INDEXED_FLAG | CHILDREN_CACHED;

  private volatile int myNameId;
  private volatile VirtualDirectoryImpl myParent;
  private volatile int myFlags;
  private volatile int myId;

  public VirtualFileSystemEntry(@NotNull String name, VirtualDirectoryImpl parent, int id, @PersistentFS.Attributes int attributes) {
    myParent = parent;
    myId = id;

    if (name != FS_ROOT_FAKE_NAME) {
      storeName(name);
    }

    if (parent != null && parent != VirtualDirectoryImpl.NULL_VIRTUAL_FILE) {
      setFlagInt(IS_SYMLINK_FLAG, PersistentFS.isSymLink(attributes));
      setFlagInt(IS_SPECIAL_FLAG, PersistentFS.isSpecialFile(attributes));
      updateLinkStatus();
    }

    setFlagInt(IS_WRITABLE_FLAG, PersistentFS.isWritable(attributes));
    setFlagInt(IS_HIDDEN_FLAG, PersistentFS.isHidden(attributes));

    setModificationStamp(LocalTimeCounter.currentTime());
  }

  private void storeName(@NotNull String name) {
    myNameId = FileNameCache.storeName(name.replace('\\', '/'));   // note: on Unix-style FS names may contain backslashes
  }

  private void updateLinkStatus() {
    boolean isSymLink = is(VFileProperty.SYMLINK);
    if (isSymLink) {
      String target = myParent.getFileSystem().resolveSymLink(this);
      setLinkTarget(target != null ? FileUtil.toSystemIndependentName(target) : null);
    }
    setFlagInt(HAS_SYMLINK_FLAG, isSymLink || myParent.getFlagInt(HAS_SYMLINK_FLAG));
  }

  @Override
  @NotNull
  public String getName() {
    return FileNameCache.getVFileName(myNameId);
  }

  public int compareNameTo(@NotNull String name, boolean ignoreCase) {
    return FileNameCache.compareNameTo(myNameId, name, ignoreCase);
  }

  static int compareNames(@NotNull String name1, @NotNull String name2, boolean ignoreCase) {
    return compareNames(name1, name2, ignoreCase, 0);
  }

  static int compareNames(@NotNull String name1, @NotNull String name2, boolean ignoreCase, int offset2) {
    int d = name1.length() - name2.length() + offset2;
    if (d != 0) return d;
    for (int i=0; i<name1.length(); i++) {
      // com.intellij.openapi.util.text.StringUtil.compare(String,String,boolean) inconsistent
      d = StringUtil.compare(name1.charAt(i), name2.charAt(i + offset2), ignoreCase);
      if (d != 0) return d;
    }
    return 0;
  }

  @Override
  public VirtualFileSystemEntry getParent() {
    return myParent;
  }

  @Override
  public boolean isDirty() {
    return (myFlags & DIRTY_FLAG) != 0;
  }

  @Override
  public long getModificationStamp() {
    return myFlags & ~ALL_FLAGS_MASK;
  }

  public synchronized void setModificationStamp(long modificationStamp) {
    myFlags = (myFlags & ALL_FLAGS_MASK) | ((int)modificationStamp & ~ALL_FLAGS_MASK);
  }

  boolean getFlagInt(int mask) {
    assert (mask & ~ALL_FLAGS_MASK) == 0 : "Unexpected flag";
    return (myFlags & mask) != 0;
  }

  synchronized void setFlagInt(int mask, boolean value) {
    assert (mask & ~ALL_FLAGS_MASK) == 0 : "Unexpected flag";
    if (value) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
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
      VirtualDirectoryImpl parent = myParent;
      if (parent != null) parent.markDirty();
    }
  }

  protected void markDirtyInternal() {
    setFlagInt(DIRTY_FLAG, true);
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  protected char[] appendPathOnFileSystem(int accumulatedPathLength, int[] positionRef) {
    return FileNameCache.appendPathOnFileSystem(myNameId, myParent, accumulatedPathLength, positionRef);
  }

  protected static int copyString(@NotNull char[] chars, int pos, @NotNull String s) {
    int length = s.length();
    s.getChars(0, length, chars, pos);
    return pos + length;
  }

  @Override
  @NotNull
  public String getUrl() {
    String protocol = getFileSystem().getProtocol();
    int prefixLen = protocol.length() + "://".length();
    int[] pos = {prefixLen};
    char[] chars = appendPathOnFileSystem(prefixLen, pos);
    copyString(chars, copyString(chars, 0, protocol), "://");
    return chars.length == pos[0] ? StringFactory.createShared(chars) : new String(chars, 0, pos[0]);
  }

  @Override
  @NotNull
  public String getPath() {
    int[] pos = {0};
    char[] chars = appendPathOnFileSystem(0, pos);
    return chars.length == pos[0] ? StringFactory.createShared(chars) : new String(chars, 0, pos[0]);
  }

  @Override
  public void delete(final Object requestor) throws IOException {
    ourPersistence.deleteFile(requestor, this);
  }

  @Override
  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

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

    return EncodingRegistry.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return ourPersistence.copyFile(requestor, VirtualFileSystemEntry.this, newParent, copyName);
      }
    });
  }

  @Override
  public void move(final Object requestor, @NotNull final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        ourPersistence.moveFile(requestor, VirtualFileSystemEntry.this, newParent);
        return VirtualFileSystemEntry.this;
      }
    });
  }

  @Override
  public int getId() {
    return myId;
  }

  @Override
  public int hashCode() {
    int id = myId;
    return id >= 0 ? id : -id;
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildDirectory(requestor, this, name);
  }

  private static void validateName(String name) throws IOException {
    if (name == null || name.isEmpty()) throw new IOException("File name cannot be empty");
    if (name.indexOf('/') >= 0 || name.indexOf(File.separatorChar) >= 0) {
      throw new IOException("File name cannot contain file path separators: '" + name + "'");
    }
  }

  @Override
  public boolean exists() {
    return ourPersistence.exists(this);
  }

  @Override
  public boolean isValid() {
    return exists();
  }

  public String toString() {
    return getUrl();
  }

  public void setNewName(@NotNull final String newName) {
    if (newName.isEmpty()) {
      throw new IllegalArgumentException("Name of the virtual file cannot be set to empty string");
    }

    myParent.removeChild(this);
    storeName(newName);
    myParent.addChild(this);
  }

  public void setParent(@NotNull final VirtualFile newParent) {
    myParent.removeChild(this);
    myParent = (VirtualDirectoryImpl)newParent;
    myParent.addChild(this);
    updateLinkStatus();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    myId = -Math.abs(myId);
  }

  @Override
  public Charset getCharset() {
    return isCharsetSet() ? super.getCharset() : computeCharset();
  }

  private Charset computeCharset() {
    Charset charset;
    if (isDirectory()) {
      Charset configured = EncodingManager.getInstance().getEncoding(this, true);
      charset = configured == null ? Charset.defaultCharset() : configured;
      setCharset(charset);
    }
    else if (SingleRootFileViewProvider.isTooLargeForContentLoading(this)) {
      charset = super.getCharset();
    }
    else {
      try {
        final byte[] content;
        try {
          content = contentsToByteArray();
        }
        catch (FileNotFoundException e) {
          // file has already been deleted on disk
          return super.getCharset();
        }
        charset = LoadTextUtil.detectCharsetAndSetBOM(this, content);
      }
      catch (FileTooBigException e) {
        return super.getCharset();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return charset;
  }

  @Override
  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS && !isDirectory()) {
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

  public void updateProperty(String property, boolean value) {
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
      VirtualDirectoryImpl parent = myParent;
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
