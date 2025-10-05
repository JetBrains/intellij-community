// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

@ApiStatus.Internal
public final class ChildInfoImpl implements ChildInfo {
  public static final int UNKNOWN_ID_YET = -238;

  private static final FileAttributes UNKNOWN = new FileAttributes(false, false, false, false, 0, 0, false);

  private final FileAttributes attributes;
  private final int id;
  private final int nameId;
  private final String symLinkTarget;
  private final ChildInfo @Nullable("null means children are unknown") [] children;

  public ChildInfoImpl(int nameId,
                       @Nullable FileAttributes attributes,
                       ChildInfo @Nullable [] children,
                       @Nullable String symLinkTarget) {
    this(UNKNOWN_ID_YET, nameId, attributes, children, symLinkTarget);
  }

  public ChildInfoImpl(int fileId,
                       int nameId,
                       @Nullable FileAttributes attributes,
                       ChildInfo @Nullable [] children,
                       @Nullable String symLinkTarget) {
    this.attributes = null == attributes ? UNKNOWN : attributes;
    this.id = fileId;
    this.nameId = nameId;
    this.symLinkTarget = symLinkTarget;
    this.children = children;
    if (fileId <= 0 && fileId != UNKNOWN_ID_YET) {
      int parentId = FSRecords.getInstance().getParent(fileId);
      throw new IllegalArgumentException("fileId is invalid: fileId=" + fileId + ", parentId=" + parentId + ", nameId=" + nameId);
    }
    else if (nameId <= 0 && nameId != UNKNOWN_ID_YET) {
      int parentId = FSRecords.getInstance().getParent(fileId);
      throw new IllegalArgumentException("nameId is invalid: fileId=" + fileId + ", parentId=" + parentId + ", nameId=" + nameId);
    }
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public @NotNull CharSequence getName() {
    return ((PersistentFSImpl)PersistentFS.getInstance()).getNameByNameId(nameId);
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
  public @Nullable FileAttributes getFileAttributes() {
    return attributes == UNKNOWN ? null : attributes;
  }

  @Override
  public @PersistentFS.Attributes int getFileAttributeFlags() {
    if (attributes == UNKNOWN) return -1;
    var isDirectory = attributes.isDirectory();
    var isWritable = attributes.isWritable();
    var isSymLink = attributes.isSymLink();
    var isSpecial = attributes.isSpecial();
    var isHidden = attributes.isHidden();
    var sensitivity = attributes.areChildrenCaseSensitive();
    var isCaseSensitive = sensitivity.isSensitive();
    var isCaseSensitivityKnown = sensitivity.isKnown();
    return PersistentFSImpl.fileAttributesToFlags(isDirectory, isWritable, isSymLink, isSpecial, isHidden, isCaseSensitivityKnown, isCaseSensitive);
  }

  private ChildInfoImpl(FileAttributes attributes, int id, int nameId, String symLinkTarget, ChildInfo @Nullable [] children) {
    this.attributes = attributes;
    this.id = id;
    this.nameId = nameId;
    this.symLinkTarget = symLinkTarget;
    this.children = children;
  }

  public @NotNull ChildInfo withChildren(ChildInfo @Nullable [] children) {
    return new ChildInfoImpl(attributes, id, nameId, symLinkTarget, children);
  }

  public @NotNull ChildInfo withNameId(int nameId) {
    return new ChildInfoImpl(attributes, id, nameId, symLinkTarget, children);
  }

  public @NotNull ChildInfo withId(int id) {
    return new ChildInfoImpl(attributes, id, nameId, symLinkTarget, children);
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

  @Override
  public String toString() {
    return "\"" + (nameId > 0 ? getName() : "?") + "\"; nameId: " + nameId + "; id: " + id +
           " (" + (attributes == UNKNOWN ? "unknown" : attributes.toString()) + ")" +
           (children == null ? "" : "\n  " + StringUtil.join(children, info -> info.toString().replaceAll("\n", "\n  "), "\n  "));
  }
}
