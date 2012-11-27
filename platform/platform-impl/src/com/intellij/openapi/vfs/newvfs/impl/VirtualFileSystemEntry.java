/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author max
 */
public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];

  protected static final PersistentFS ourPersistence = PersistentFS.getInstance();

  private static final Key<String> SYMLINK_TARGET = Key.create("local.vfs.symlink.target");

  private static final int DIRTY_FLAG = 0x0100;
  private static final int IS_SYMLINK_FLAG = 0x0200;
  private static final int HAS_SYMLINK_FLAG = 0x0400;
  private static final int IS_SPECIAL_FLAG = 0x0800;
  private static final int INT_FLAGS_MASK = 0xff00;

  @NonNls private static final String EMPTY = "";
  @NonNls private static final String[] WELL_KNOWN_SUFFIXES = {"$1.class", "$2.class", ".class", ".java", ".html", ".txt", ".xml"};

  /** Either a String or byte[]. Possibly should be concatenated with one of the entries in the {@link #WELL_KNOWN_SUFFIXES}. */
  private volatile Object myName;
  private volatile VirtualDirectoryImpl myParent;
  /** Also, high three bits are used as an index into the {@link #WELL_KNOWN_SUFFIXES} array. */
  private volatile short myFlags = 0;
  private volatile int myId;

  public VirtualFileSystemEntry(@NotNull String name, VirtualDirectoryImpl parent, int id, @PersistentFS.Attributes int attributes) {
    myParent = parent;
    myId = id;

    storeName(name);

    if (parent != null) {
      setFlagInt(IS_SYMLINK_FLAG, PersistentFS.isSymLink(attributes));
      setFlagInt(IS_SPECIAL_FLAG, PersistentFS.isSpecialFile(attributes));
      updateLinkStatus();
    }
  }

  private void storeName(@NotNull String name) {
    myFlags &= 0x1fff;
    for (int i = 0; i < WELL_KNOWN_SUFFIXES.length; i++) {
      String suffix = WELL_KNOWN_SUFFIXES[i];
      if (name.endsWith(suffix)) {
        name = StringUtil.trimEnd(name, suffix);
        int mask = (i+1) << 13;
        myFlags |= mask;
        break;
      }
    }

    myName = encodeName(name.replace('\\', '/'));  // note: on Unix-style FS names may contain backslashes
  }

  private void updateLinkStatus() {
    boolean isSymLink = isSymLink();
    if (isSymLink) {
      String target = myParent.getFileSystem().resolveSymLink(this);
      putUserData(SYMLINK_TARGET, target != null ? FileUtil.toSystemIndependentName(target) : target);
    }
    setFlagInt(HAS_SYMLINK_FLAG, isSymLink || ((VirtualFileSystemEntry)myParent).getFlagInt(HAS_SYMLINK_FLAG));
  }

  private static Object encodeName(@NotNull String name) {
    int length = name.length();
    if (length == 0) return EMPTY;

    if (!IOUtil.isAscii(name)) {
      return name;
    }

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte)name.charAt(i);
    }
    return bytes;
  }

  @NotNull
  private String getEncodedSuffix() {
    int index = (myFlags >> 13) & 0x07;
    if (index == 0) return EMPTY;
    return WELL_KNOWN_SUFFIXES[index-1];
  }

  @Override
  @NotNull
  public String getName() {
    Object name = rawName();
    String suffix = getEncodedSuffix();
    if (name instanceof String) {
      //noinspection StringEquality
      return suffix == EMPTY ? (String)name : name + suffix;
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    char[] chars = new char[length + suffix.length()];
    for (int i = 0; i < length; i++) {
      chars[i] = (char)bytes[i];
    }
    copyString(chars, length, suffix);
    return new String(chars);
  }

  boolean nameMatches(@NotNull String pattern, boolean ignoreCase) {
    Object name = rawName();
    String suffix = getEncodedSuffix();
    if (name instanceof String) {
      final String nameStr = (String)name;
      return pattern.length() == nameStr.length() + suffix.length() &&
             pattern.regionMatches(ignoreCase, 0, nameStr, 0, nameStr.length()) &&
             pattern.regionMatches(ignoreCase, nameStr.length(), suffix, 0, suffix.length());
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    if (length + suffix.length() != pattern.length()) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (!StringUtil.charsMatch((char)bytes[i], pattern.charAt(i), ignoreCase)) {
        return false;
      }
    }

    return pattern.regionMatches(ignoreCase, length, suffix, 0, suffix.length());
  }

  protected Object rawName() {
    return myName;
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
  public boolean getFlag(int mask) {
    assert (mask & INT_FLAGS_MASK) == 0 : "Mask '" + Integer.toBinaryString(mask) + "' is in reserved range.";
    return getFlagInt(mask);
  }

  private boolean getFlagInt(int mask) {
    return (myFlags & mask) != 0;
  }

  @Override
  public void setFlag(int mask, boolean value) {
    assert (mask & INT_FLAGS_MASK) == 0 : "Mask '" + Integer.toBinaryString(mask) + "' is in reserved range.";
    setFlagInt(mask, value);
  }

  private void setFlagInt(int mask, boolean value) {
    if (value) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
  }

  @Override
  public void markClean() {
    setFlagInt(DIRTY_FLAG, false);
  }

  @Override
  public void markDirty() {
    if (!isDirty()) {
      setFlagInt(DIRTY_FLAG, true);
      if (myParent != null) myParent.markDirty();
    }
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  protected char[] appendPathOnFileSystem(int pathLength, int[] position) {
    Object o = rawName();
    String suffix = getEncodedSuffix();
    int rawNameLength = o instanceof String ? ((String)o).length() : ((byte[])o).length;
    int nameLength = rawNameLength + suffix.length();
    boolean appendSlash = SystemInfo.isWindows && myParent == null && suffix.length() == 0 && rawNameLength == 2 &&
                          (o instanceof String ? ((String)o).charAt(1) : (char)((byte[])o)[1]) == ':';

    char[] chars;
    if (myParent != null) {
      chars = myParent.appendPathOnFileSystem(pathLength + 1 + nameLength, position);
      if (position[0] > 0 && chars[position[0] - 1] != '/') {
        chars[position[0]++] = '/';
      }
    }
    else {
      int rootPathLength = pathLength + nameLength;
      if (appendSlash) ++rootPathLength;
      chars = new char[rootPathLength];
    }

    if (o instanceof String) {
      position[0] = copyString(chars, position[0], (String)o);
    }
    else {
      byte[] bytes = (byte[])o;
      int pos = position[0];
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, len = bytes.length; i < len; i++) {
        chars[pos++] = (char)bytes[i];
      }
      position[0] = pos;
    }

    if (appendSlash) {
      chars[position[0]++] = '/';
    }
    else {
      position[0] = copyString(chars, position[0], suffix);
    }

    return chars;
  }

  private static int copyString(@NotNull char[] chars, int pos, @NotNull String s) {
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
    return new String(chars, 0, pos[0]);
  }

  @Override
  @NotNull
  public String getPath() {
    int[] pos = {0};
    char[] chars = appendPathOnFileSystem(0, pos);
    return new String(chars, 0, pos[0]);
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
    return ourPersistence.isWritable(this);
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
    if (name == null || name.length() == 0) throw new IOException("File name cannot be empty");
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
    if (newName.length() == 0) {
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
    Charset charset;
    if (isCharsetSet()) {
      charset = super.getCharset();
    }
    else {
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
            // file has already been deleted from disk
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
    }
    return charset;
  }

  @Override
  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS && !isDirectory()) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.length() == 0 ? getName() : nameWithoutExtension;
    }
    return getName();
  }

  @Override
  public boolean isSymLink() {
    return getFlagInt(IS_SYMLINK_FLAG);
  }

  @Override
  public boolean isSpecialFile() {
    return getFlagInt(IS_SPECIAL_FLAG);
  }

  @Override
  public String getCanonicalPath() {
    if (getFlagInt(HAS_SYMLINK_FLAG)) {
      if (isSymLink()) {
        return getUserData(SYMLINK_TARGET);
      }
      else if (myParent != null) {
        return myParent.getCanonicalPath() + "/" + getName();
      }
      else {
        return getName();
      }
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
