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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];
  protected static final PersistentFS ourPersistence = (PersistentFS)ManagingFS.getInstance();
  private static final byte DIRTY_FLAG = 0x01;
  private static final String EMPTY = "";

  private volatile Object myName;
  private volatile VirtualDirectoryImpl myParent;
  private volatile byte myFlags = 0;
  private volatile int myId;

  public VirtualFileSystemEntry(final String name, final VirtualDirectoryImpl parent, int id) {
    myName = encodeName(name.replace('\\', '/'));  // note: on Unix-style FS names may contain backslashes
    myParent = parent;
    myId = id;
  }

  protected static Object encodeName(String name) {
    int length = name.length();
    if (length == 0) return EMPTY;

    if (!IOUtil.isAscii(name)) return name;

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte)name.charAt(i);
    }
    return bytes;
  }

  @NotNull
  public String getName() {
    Object name = rawName();
    if (name instanceof String) {
      return (String)name;
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    char[] chars = new char[length];
    for (int i = 0; i < length; i++) {
      chars[i] = (char)bytes[i];
    }
    return new String(chars);
  }

  boolean nameMatches(String pattern, boolean ignoreCase) {
    Object name = rawName();
    if (name instanceof String) {
      return ignoreCase ? pattern.equals(name) : pattern.equalsIgnoreCase((String)name);
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    if (length != pattern.length()) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (!charsMatch(ignoreCase, (char)bytes[i], pattern.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  private static boolean charsMatch(boolean ignoreCase, char c1, char c2) {
    // duplicating String.equalsIgnoreCase logic
    if (c1 == c2) {
      return true;
    }
    if (ignoreCase) {
      // If characters don't match but case may be ignored,
      // try converting both characters to uppercase.
      // If the results match, then the comparison scan should
      // continue.
      char u1 = Character.toUpperCase(c1);
      char u2 = Character.toUpperCase(c2);
      if (u1 == u2) {
        return true;
      }
      // Unfortunately, conversion to uppercase does not work properly
      // for the Georgian alphabet, which has strange rules about case
      // conversion.  So we need to make one last check before
      // exiting.
      if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
        return true;
      }
    }
    return false;
  }

  protected final Object rawName() {
    return myName;
  }

  public VirtualFileSystemEntry getParent() {
    return myParent;
  }

  public boolean isDirty() {
    return (myFlags & DIRTY_FLAG) != 0;
  }

  public void setFlag(int flag_mask, boolean value) {
    if (value) {
      myFlags |= flag_mask;
    }
    else {
      myFlags &= ~flag_mask;
    }
  }

  public boolean getFlag(int flag_mask) {
    return (myFlags & flag_mask) != 0;
  }

  public void markClean() {
    setFlag(DIRTY_FLAG, false);
  }

  public void markDirty() {
    if (!isDirty()) {
      setFlag(DIRTY_FLAG, true);
      if (myParent != null) myParent.markDirty();
    }
  }

  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  protected int getPathLength() {
    Object o = rawName();
    int length = o instanceof String ? ((String)o).length() : ((byte[]) o).length;
    return myParent == null ? length : myParent.getPathLength() + length + 1;
  }

  protected int appendPathOnFileSystem(char[] chars, int pos) {
    if (myParent != null) {
      pos = myParent.appendPathOnFileSystem(chars, pos);
    }

    Object o = rawName();
    if (o == EMPTY) return pos;

    if (pos > 0 && chars[pos - 1] != '/') {
      chars[pos++] = '/';
    }

    if (o instanceof String) {
      return copyString(chars, pos, (String)o);
    }
    else {
      byte[] bytes = (byte[]) o;
      int len = bytes.length;
      for (int i = 0; i < len; i++) {
        chars[pos++] = (char)bytes[i];
      }
    }
    return pos;
  }

  private static int copyString(char[] chars, int pos, String s) {
    int length = s.length();
    s.getChars(0, length, chars, pos);
    return pos + length;
  }

  @NotNull
  public String getUrl() {
    String protocol = getFileSystem().getProtocol();
    char[] chars = new char[getPathLength() + protocol.length() + "://".length()];
    int pos = copyString(chars, 0, protocol);
    pos = copyString(chars, pos, "://");

    return getPathImpl(chars, pos);
  }

  @NotNull
  public String getPath() {
    char[] chars = new char[getPathLength()];
    return getPathImpl(chars, 0);
  }

  private String getPathImpl(char[] chars, int pos) {
    int count = appendPathOnFileSystem(chars, pos);
    return new String(chars, 0, count);
  }

  public void delete(final Object requestor) throws IOException {
    ourPersistence.deleteFile(requestor, this);
  }

  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!VfsUtil.isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    ourPersistence.renameFile(requestor, this, newName);
  }

  @NotNull
  public VirtualFile createChildData(final Object requestor, @NotNull final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildFile(requestor, this, name);
  }

  public boolean isWritable() {
    return ourPersistence.isWritable(this);
  }

  public void setWritable(boolean writable) throws IOException {
    ourPersistence.setWritable(this, writable);
  }

  public long getTimeStamp() {
    return ourPersistence.getTimeStamp(this);
  }

  public void setTimeStamp(final long time) throws IOException {
    ourPersistence.setTimeStamp(this, time);
  }

  public long getLength() {
    return ourPersistence.getLength(this);
  }

  public VirtualFile copy(final Object requestor, final VirtualFile newParent, final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return VfsUtil.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      public VirtualFile compute() throws IOException {
        return ourPersistence.copyFile(requestor, VirtualFileSystemEntry.this, newParent, copyName);
      }
    });
  }

  public void move(final Object requestor, final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    VfsUtil.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      public VirtualFile compute() throws IOException {
        ourPersistence.moveFile(requestor, VirtualFileSystemEntry.this, newParent);
        return VirtualFileSystemEntry.this;
      }
    });
  }

  public int getId() {
    return myId;
  }

  @Override
  public int hashCode() {
    int id = myId;
    return id >= 0 ? id : -id;
  }

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

  public boolean exists() {
    return ourPersistence.exists(this);
  }


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
    myName = encodeName(newName.replace('\\', '/'));
    myParent.addChild(this);
  }

  public void setParent(@NotNull final VirtualFile newParent) {
    myParent.removeChild(this);
    myParent = (VirtualDirectoryImpl)newParent;
    myParent.addChild(this);
  }

  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    myId = -Math.abs(myId);
  }

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

  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS && !isDirectory()) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.length() == 0 ? getName() : nameWithoutExtension;
    }
    return getName();
  }
}
