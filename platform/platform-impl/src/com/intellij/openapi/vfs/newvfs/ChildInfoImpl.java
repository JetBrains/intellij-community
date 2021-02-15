// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

@ApiStatus.Internal
public final class ChildInfoImpl extends FileAttributes implements ChildInfo {
  public static final int UNKNOWN_ID_YET = -238;

  private final int id;
  private final int nameId;
  private final String symLinkTarget;
  private final ChildInfo @Nullable("null means children are unknown") [] children;

  public ChildInfoImpl(@NotNull String name,
                       @Nullable FileAttributes attributes,
                       ChildInfo @Nullable [] children,
                       @Nullable String symLinkTarget) {
    this(UNKNOWN_ID_YET, FileNameCache.storeName(name), attributes, children, symLinkTarget);
  }

  public ChildInfoImpl(int id,
                       int nameId,
                       @Nullable FileAttributes attributes,
                       ChildInfo @Nullable [] children,
                       @Nullable String symLinkTarget) {
    super(attributes == null ? UNKNOWN : attributes);
    this.nameId = nameId;
    this.id = id;
    this.children = children;
    this.symLinkTarget = symLinkTarget;
    if (id <= 0 && id != UNKNOWN_ID_YET || nameId <= 0 && nameId != UNKNOWN_ID_YET) {
      throw new IllegalArgumentException("invalid arguments id: "+id+"; nameId: "+nameId);
    }
  }

  @Override
  public int getId() {
    return id;
  }

  @NotNull
  @Override
  public CharSequence getName() {
    return FileNameCache.getVFileName(nameId);
  }

  @Override
  public int getNameId() {
    return nameId;
  }

  @Override
  public String getSymlinkTarget() {
    return symLinkTarget;
  }

  @Override
  public ChildInfo @Nullable [] getChildren() {
    return children;
  }

  @Override
  public FileAttributes getFileAttributes() {
    return flags == -1 ? null : this;
  }

  @Override
  @PersistentFS.Attributes
  public int getFileAttributeFlags() {
    if (flags == -1) return -1;
    FileAttributes.Type type = getType();
    boolean isDirectory = type == FileAttributes.Type.DIRECTORY;
    boolean isWritable = !BitUtil.isSet(flags, FileAttributes.READ_ONLY);
    boolean isSymLink = BitUtil.isSet(flags, FileAttributes.SYM_LINK);
    boolean isSpecial = type == FileAttributes.Type.SPECIAL;
    boolean isHidden = BitUtil.isSet(flags, FileAttributes.HIDDEN);
    CaseSensitivity sensitivity = areChildrenCaseSensitive();
    boolean isCaseSensitive = sensitivity == CaseSensitivity.SENSITIVE;
    return PersistentFSImpl.fileAttributesToFlags(isDirectory, isWritable, isSymLink, isSpecial, isHidden, sensitivity != CaseSensitivity.UNKNOWN, isCaseSensitive);
  }

  @Override
  @NonNls
  public String toString() {
    return (nameId > 0 ? getName() : "?")+"; nameId: "+nameId + "; id: " + id + " (" + (flags == -1 ? "unknown" : super.toString()) + ")" +
           (children == null ? "" : "\n  " + StringUtil.join(children, info -> info.toString().replaceAll("\n", "\n  "), "\n  "));
  }

  private ChildInfoImpl(int id,
                       int nameId,
                       String symLinkTarget,
                       ChildInfo @Nullable("null means children are unknown") [] children,
                       byte flags,
                       long length,
                       long lastModified) {
    super(flags, length, lastModified);
    this.id = id;
    this.nameId = nameId;
    this.symLinkTarget = symLinkTarget;
    this.children = children;
  }

  @NotNull
  public ChildInfo withChildren(ChildInfo @Nullable [] children) {
    return new ChildInfoImpl(id, nameId, symLinkTarget, children, flags, length, lastModified);
  }
  @NotNull
  public ChildInfo withNameId(int nameId) {
    return new ChildInfoImpl(id, nameId, symLinkTarget, children, flags, length, lastModified);
  }
  @NotNull
  public ChildInfo withId(int id) {
    return new ChildInfoImpl(id, nameId, symLinkTarget, children, flags, length, lastModified);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ChildInfoImpl info = (ChildInfoImpl)o;
    return id == info.id &&
           nameId == info.nameId &&
           Objects.equals(symLinkTarget, info.symLinkTarget) &&
           Arrays.equals(children, info.children);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, nameId, symLinkTarget, Arrays.hashCode(children));
  }
}