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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
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

  protected static final PersistentFS ourPersistence = (PersistentFS)ManagingFS.getInstance();

  private static final byte DIRTY_FLAG = 0x01;
  private static final String EMPTY = "";

  /** Either a String or byte[]. Possibly should be concatenated with one of the entries in the {@link #wellKnownSuffixes}. */
  private volatile Object myName;
  private volatile VirtualDirectoryImpl myParent;
  /** Also, high three bits are used as an index into the {@link #wellKnownSuffixes} array. */
  private volatile byte myFlags = 0;
  private volatile int myId;

  public VirtualFileSystemEntry(@NotNull String name, final VirtualDirectoryImpl parent, int id) {
    storeName(name);
    myParent = parent;
    myId = id;
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

  @NonNls private static final String[] wellKnownSuffixes = { "$1.class", "$2.class", ".class", ".java", ".html", ".txt", ".xml",};
  private void storeName(@NotNull String name) {
    myFlags &= 0x1f;
    for (int i = 0; i < wellKnownSuffixes.length; i++) {
      String suffix = wellKnownSuffixes[i];
      if (name.endsWith(suffix)) {
        name = StringUtil.trimEnd(name, suffix);
        int mask = (i+1) << 5;
        myFlags |= mask;
        break;
      }
    }

    myName = encodeName(name.replace('\\', '/'));  // note: on Unix-style FS names may contain backslashes
  }

  @NotNull
  private String getEncodedSuffix() {
    int index = (myFlags >> 5) & 0x07;
    if (index == 0) return EMPTY;
    return wellKnownSuffixes[index-1];
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

  private Object rawName() {
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
  public void setFlag(int flag_mask, boolean value) {
    assert (flag_mask & 0xe0) == 0 : "Mask '"+ Integer.toBinaryString(flag_mask)+"' is not supported. High three bits are reserved.";
    if (value) {
      myFlags |= flag_mask;
    }
    else {
      myFlags &= ~flag_mask;
    }
  }

  @Override
  public boolean getFlag(int flag_mask) {
    assert (flag_mask & 0xe0) == 0 : "Mask '"+ Integer.toBinaryString(flag_mask)+"' is not supported. High three bits are reserved.";
    return (myFlags & flag_mask) != 0;
  }

  @Override
  public void markClean() {
    setFlag(DIRTY_FLAG, false);
  }

  @Override
  public void markDirty() {
    if (!isDirty()) {
      setFlag(DIRTY_FLAG, true);
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

  protected int getPathLength() {
    Object o = rawName();
    int length = o instanceof String ? ((String)o).length() : ((byte[]) o).length;
    length += getEncodedSuffix().length();
    return myParent == null ? length : myParent.getPathLength() + length + 1;
  }

  int appendPathOnFileSystem(@NotNull char[] chars, int pos) {
    if (myParent != null) {
      pos = myParent.appendPathOnFileSystem(chars, pos);
    }

    Object o = rawName();
    String suffix = getEncodedSuffix();
    //noinspection StringEquality
    if (o == EMPTY && suffix == EMPTY) {
      return pos;
    }

    if (pos > 0 && chars[pos - 1] != '/') {
      chars[pos++] = '/';
    }

    if (o instanceof String) {
      pos = copyString(chars, pos, (String)o);
      return copyString(chars, pos, suffix);
    }
    byte[] bytes = (byte[]) o;
    int len = bytes.length;
    for (int i = 0; i < len; i++) {
      chars[pos++] = (char)bytes[i];
    }
    return copyString(chars, pos, suffix);
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
    char[] chars = new char[getPathLength() + protocol.length() + "://".length()];
    int pos = copyString(chars, 0, protocol);
    pos = copyString(chars, pos, "://");

    return getPathImpl(chars, pos);
  }

  @Override
  @NotNull
  public String getPath() {
    char[] chars = new char[getPathLength()];
    return getPathImpl(chars, 0);
  }

  private String getPathImpl(@NotNull char[] chars, int pos) {
    int count = appendPathOnFileSystem(chars, pos);
    return new String(chars, 0, count);
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

  public void setName(@NotNull final String newName) {
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
    return ourPersistence.isSymLink(this);
  }

  @Override
  public boolean isSpecialFile() {
    return ourPersistence.isSpecialFile(this);
  }

  @Override
  public VirtualFile getRealFile() {
    final NewVirtualFileSystem fs = getFileSystem();
    return fs instanceof LocalFileSystem ? ((LocalFileSystem)fs).getRealFile(this) : super.getRealFile();
  }
}
