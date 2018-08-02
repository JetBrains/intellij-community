// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;

public abstract class FSRecords {
  public static final boolean weHaveContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  public static FSRecords getInstance() {
    return ApplicationManager.getApplication().getComponent(FSRecords.class);
  }

  @NotNull
  public abstract File basePath();

  public abstract void connect();

  public abstract long getCreationTimestamp();

  public abstract PersistentStringEnumerator getNames();

  public abstract int createRecord();

  public abstract int getMaxId();

  @NotNull
  public abstract NameId[] listAll(int parentId);

  @NotNull
  public abstract FileNameCache getFileNameCache();

  public abstract int getParent(int id);

  public abstract int getNameId(String name);

  public abstract String getName(int id);

  public abstract String getNameByNameId(int nameId);

  @Nullable
  public abstract DataInputStream readAttributeWithLock(int fileId, FileAttribute att);

  @NotNull
  public abstract DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att);

  public abstract void invalidateCaches();

  protected abstract void checkSanity();

  protected abstract void requestVfsRebuild(@NotNull Throwable e);

  public static class NameId {
    @NotNull
    public static final NameId[] EMPTY_ARRAY = new NameId[0];
    public final int id;
    public final CharSequence name;
    public final int nameId;

    public NameId(int id, int nameId, @NotNull CharSequence name) {
      this.id = id;
      this.nameId = nameId;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + " (" + id + ")";
    }
  }
}
