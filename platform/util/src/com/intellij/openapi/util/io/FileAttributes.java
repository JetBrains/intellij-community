// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;

import static com.intellij.util.BitUtil.isSet;

/**
 * @see FileSystemUtil#getAttributes(String)
 */
public final class FileAttributes {
  public enum Type {FILE, DIRECTORY, SPECIAL}

  public enum CaseSensitivity {
    /** Files in this directory are case-sensitive. */
    SENSITIVE,
    /** Files in this directory are case-insensitive */
    INSENSITIVE,
    /** Case sensitivity is not specified - either because not yet known or not applicable (to non-directories) */
    UNKNOWN
  }

  private static final byte SYM_LINK = 0b001;
  private static final byte HIDDEN = 0b010;
  private static final byte READ_ONLY = 0b100;
  private static final int TYPE_SHIFT = 3;
  private static final int CASE_SENSITIVITY_SHIFT = 5;

  public static final FileAttributes BROKEN_SYMLINK = new FileAttributes(SYM_LINK, 0, 0);

  /**
   * <p>Bits 0-2: modifiers ({@link #SYM_LINK}, {@link #HIDDEN}, {@link #READ_ONLY})</p>
   * <p>Bits 3-4: {@link Type Type} (00=unknown, 01={@link Type#FILE FILE}, 10={@link Type#DIRECTORY DIRECTORY}, 11={@link Type#SPECIAL SPECIAL})</p>
   * <p>Bits 5-7: {@link CaseSensitivity CaseSensitivity} (00={@link CaseSensitivity#UNKNOWN UNKNOWN},
   *   01={@link CaseSensitivity#SENSITIVE SENSITIVE}, 10={@link CaseSensitivity#INSENSITIVE INSENSITIVE})</p>
   */
  private final byte flags;

  /**
   * In bytes, 0 for special files.<br/>
   * For symlinks - length of a link target.
   */
  public final long length;

  /**
   * In milliseconds (actual resolution depends on a file system and may be less accurate).<br/>
   * For symlinks - timestamp of a link target.
   */
  public final long lastModified;

  public FileAttributes(boolean isDirectory, boolean isSpecial, boolean isSymlink, boolean isHidden, long length, long lastModified, boolean isWritable) {
    this(isDirectory, isSpecial, isSymlink, isHidden, length, lastModified, isWritable, CaseSensitivity.UNKNOWN);
  }

  /**
   * @param caseSensitivity flag for this directory case sensitivity. A directory is considered "case-sensitive" if it's able to contain
   *                        both "readme.txt" and "README.TXT" files and consider them different. Examples of case-sensitive directories are
   *                        regular directories on Linux, directories in case-sensitive volumes on macOS, or NTFS directories
   *                        configured with "fsutil.exe file setCaseSensitiveInfo" on Windows 10+.<br/>
   *                        When {@code isDirectory == false}, the caseSensitivity argument is ignored
   *                        (set to {@link CaseSensitivity#UNKNOWN}), because case sensitivity is configured on a directory level.
   */
  public FileAttributes(boolean isDirectory, boolean isSpecial, boolean isSymlink, boolean isHidden, long length, long lastModified, boolean isWritable,
                        @NotNull CaseSensitivity caseSensitivity) {
    this(flags(isDirectory, isSpecial, isSymlink, isHidden, isWritable, caseSensitivity), length, lastModified);
  }

  private FileAttributes(byte flags, long length, long lastModified) {
    if (flags != -1 && (flags & 0b10000000) != 0) {
      throw new IllegalArgumentException("Invalid flags: " + Integer.toBinaryString(flags));
    }
    if (length < 0) {
      throw new IllegalArgumentException("Invalid length: " + length);
    }
    this.flags = flags;
    this.length = length;
    this.lastModified = lastModified;
  }

  private static byte flags(boolean isDirectory, boolean isSpecial, boolean isSymlink, boolean isHidden, boolean isWritable, CaseSensitivity sensitivity) {
    byte flags = 0;
    if (isSymlink) flags |= SYM_LINK;
    if (isHidden) flags |= HIDDEN;
    if (!isWritable) flags |= READ_ONLY;
    int type_flags = (isSpecial ? 0b11 : isDirectory ? 0b10 : 0b01) << TYPE_SHIFT;
    flags |= type_flags;
    flags = packSensitivityIntoFlags(isDirectory ? sensitivity : CaseSensitivity.UNKNOWN, flags);
    return flags;
  }

  private static byte packSensitivityIntoFlags(CaseSensitivity sensitivity, byte flags) {
    int sensitivity_flags = sensitivity == CaseSensitivity.UNKNOWN ? 0 : sensitivity == CaseSensitivity.SENSITIVE ? 1 : 2;
    flags |= sensitivity_flags << CASE_SENSITIVITY_SHIFT;
    return flags;
  }

  public boolean isFile() {
    return ((flags >> TYPE_SHIFT) & 0b11) == 0b01;
  }

  public boolean isDirectory() {
    return ((flags >> TYPE_SHIFT) & 0b11) == 0b10;
  }

  public boolean isSpecial() {
    return !isDirectory() && ((flags >> TYPE_SHIFT) & 0b11) == 0b11;
  }

  public boolean isSymLink() {
    return isSet(flags, SYM_LINK);
  }

  public boolean isHidden() {
    return isSet(flags, HIDDEN);
  }

  public boolean isWritable() {
    return !isSet(flags, READ_ONLY);
  }

  public @Nullable("`null` means an unknown type, typically a broken symlink") Type getType() {
    int type = (flags >> TYPE_SHIFT) & 0b11;
    switch (type) {
      case 0b00: return null;
      case 0b01: return Type.FILE;
      case 0b10: return Type.DIRECTORY;
      case 0b11: return Type.SPECIAL;
    }
    throw new IllegalStateException("Invalid type flags: " + Integer.toBinaryString(flags));
  }

  public @NotNull CaseSensitivity areChildrenCaseSensitive() {
    if (!isDirectory()) {
      return CaseSensitivity.UNKNOWN;
    }
    int sensitivity_flags = (flags >> CASE_SENSITIVITY_SHIFT) & 0b11;
    switch (sensitivity_flags) {
      case 0b00: return CaseSensitivity.UNKNOWN;
      case 0b01: return CaseSensitivity.SENSITIVE;
      case 0b10: return CaseSensitivity.INSENSITIVE;
    }
    throw new IllegalStateException("Invalid sensitivity flags: " + Integer.toBinaryString(sensitivity_flags));
  }

  public @NotNull FileAttributes withCaseSensitivity(@NotNull CaseSensitivity sensitivity) {
    byte newFlags = (byte)(flags & ~(0b11 << CASE_SENSITIVITY_SHIFT));
    newFlags = packSensitivityIntoFlags(sensitivity, newFlags);
    return new FileAttributes(newFlags, length, lastModified);
  }

  public @NotNull FileAttributes withTimeStamp(long timestamp) {
    return new FileAttributes(flags, length, timestamp);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileAttributes that = (FileAttributes)o;
    if (flags != that.flags) return false;
    if (lastModified != that.lastModified) return false;
    if (length != that.length) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = flags;
    result = 31 * result + (int)(length ^ (length >>> 32));
    result = 31 * result + (int)(lastModified ^ (lastModified >>> 32));
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[type:").append(getType());
    if (isSet(flags, SYM_LINK)) sb.append(" l");
    if (isSet(flags, HIDDEN)) sb.append(" .");
    if (isSet(flags, READ_ONLY)) sb.append(" ro");
    sb.append(" length:").append(length);
    sb.append(" modified:").append(lastModified);
    sb.append(" case-sensitive: ").append(areChildrenCaseSensitive());
    sb.append(']');
    return sb.toString();
  }

  public static @NotNull FileAttributes fromNio(@NotNull Path path, @NotNull BasicFileAttributes attrs) {
    boolean isSymbolicLink =
      attrs.isSymbolicLink() ||
      SystemInfo.isWindows && attrs.isOther() && attrs.isDirectory() && path.getParent() != null;  // marking reparse points as symlinks (except roots)

    if (isSymbolicLink) {
      try {
        attrs = Files.readAttributes(path, BasicFileAttributes.class);
      }
      catch (IOException e) {
        return BROKEN_SYMLINK;
      }
    }

    boolean isHidden = false;
    boolean isWritable = false;
    if (SystemInfo.isWindows) {
      isHidden = path.getParent() != null && ((DosFileAttributes)attrs).isHidden();
      isWritable = attrs.isDirectory() || !((DosFileAttributes)attrs).isReadOnly();
    }
    else {
      try { isWritable = attrs.isDirectory() || Files.isWritable(path); }
      catch (SecurityException ignored) { }
    }

    long lastModified = attrs.lastModifiedTime().toMillis();

    boolean isSpecial = attrs.isOther() && !(SystemInfo.isWindows && attrs.isDirectory());  // reparse points are directories (not special files)
    return new FileAttributes(attrs.isDirectory(), isSpecial, isSymbolicLink, isHidden, attrs.size(), lastModified, isWritable);
  }
}
