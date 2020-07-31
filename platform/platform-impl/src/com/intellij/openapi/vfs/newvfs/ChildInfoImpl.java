// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class ChildInfoImpl implements ChildInfo {
  public static final int UNKNOWN_ID_YET = -238;

  private final int id;
  private final int nameId;
  private final String symLinkTarget;
  private final ChildInfo @Nullable("null means children are unknown") [] children;

  private final byte fileAttributesType;  // inlined FileAttributes to reduce memory
  private final @FileAttributes.Flags byte flags; // -1 means getFileAttributes == null
  private final long length;
  private final long lastModified;

  public ChildInfoImpl(int id, @NotNull String name, @Nullable FileAttributes attributes, ChildInfo @Nullable [] children, @Nullable String symLinkTarget) {
    this(id, FileNameCache.storeName(name), attributes, children, symLinkTarget);
  }

  public ChildInfoImpl(int id,
                       int nameId,
                       @Nullable FileAttributes attributes,
                       ChildInfo @Nullable [] children,
                       @Nullable String symLinkTarget) {
    this.nameId = nameId;
    this.id = id;
    this.children = children;
    this.symLinkTarget = symLinkTarget;
    if (id <= 0 && id != UNKNOWN_ID_YET || nameId <= 0 && nameId != UNKNOWN_ID_YET) throw new IllegalArgumentException("invalid arguments id: "+id+"; nameId: "+nameId);
    if (attributes == null) {
      fileAttributesType = -1;
      flags = -1;
      length = 0;
      lastModified = 0;
    }
    else {
      fileAttributesType = attributes.type == null ? -1 : (byte)attributes.type.ordinal();
      flags = attributes.flags;
      length = attributes.length;
      lastModified = attributes.lastModified;
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
    return flags == -1 ? null : FileAttributes.createFrom(fileAttributesType, flags, length, lastModified);
  }

  @Override
  public String toString() {
    return (nameId > 0 ? getName() : "?")+"; nameId: "+nameId + "; id: " + id + " (" + getFileAttributes() + ")" +
           (children == null ? "" : "\n  " + StringUtil.join(children, info -> info.toString().replaceAll("\n", "\n  "), "\n  "));
  }
}