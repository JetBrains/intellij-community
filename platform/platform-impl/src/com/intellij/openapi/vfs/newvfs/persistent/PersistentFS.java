// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Function;

import static com.intellij.util.BitUtil.isSet;

public abstract class PersistentFS extends ManagingFS {
  static final class Flags {
    static final int CHILDREN_CACHED = 0x01;
    static final int IS_DIRECTORY = 0x02;
    static final int IS_READ_ONLY = 0x04;
    static final int MUST_RELOAD_CONTENT = 0x08;
    static final int IS_SYMLINK = 0x10;
    static final int IS_SPECIAL = 0x20;
    static final int IS_HIDDEN = 0x40;
    static final int MUST_RELOAD_LENGTH = 0x80;
    static final int CHILDREN_CASE_SENSITIVE = 0x100;  // 'true' if this directory can contain files differing only in the case
    static final int CHILDREN_CASE_SENSITIVITY_CACHED = 0x200;  // 'true' if this directory's case sensitivity is known
    static final int FREE_RECORD_FLAG = 0x400;
    static final int OFFLINE_BY_DEFAULT = 0x800;

    static final int MASK = 0xFFF;
  }

  @MagicConstant(flagsFromClass = Flags.class)
  @Target(ElementType.TYPE_USE)
  public @interface Attributes { }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static PersistentFS getInstance() {
    return (PersistentFS)ManagingFS.getInstance();
  }

  @Override
  protected @NotNull <P, R> Function<P, R> accessDiskWithCheckCanceled(Function<? super P, ? extends R> function) {
    return new DiskQueryRelay<>(function)::accessDiskWithCheckCanceled;
  }

  public abstract void clearIdCache();

  public abstract String @NotNull [] listPersisted(@NotNull VirtualFile parent);

  @ApiStatus.Internal
  public abstract @NotNull List<? extends ChildInfo> listAll(@NotNull VirtualFile parent);

  @ApiStatus.Internal
  public abstract ChildInfo findChildInfo(@NotNull VirtualFile parent, @NotNull String childName, @NotNull NewVirtualFileSystem fs);

  @NotNull
  public abstract String getName(int id);

  public abstract long getLastRecordedLength(@NotNull VirtualFile file);

  public abstract boolean isHidden(@NotNull VirtualFile file);

  public abstract @Attributes int getFileAttributes(int id);

  public static boolean isDirectory(@Attributes int attributes) { return isSet(attributes, Flags.IS_DIRECTORY); }
  public static boolean isWritable(@Attributes int attributes) { return !isSet(attributes, Flags.IS_READ_ONLY); }
  public static boolean isSymLink(@Attributes int attributes) { return isSet(attributes, Flags.IS_SYMLINK); }
  public static boolean isSpecialFile(@Attributes int attributes) { return !isDirectory(attributes) && isSet(attributes, Flags.IS_SPECIAL); }
  public static boolean isHidden(@Attributes int attributes) { return isSet(attributes, Flags.IS_HIDDEN); }
  public static boolean isOfflineByDefault(@Attributes int attributes) { return isSet(attributes, Flags.OFFLINE_BY_DEFAULT); }

  public static @NotNull FileAttributes.CaseSensitivity areChildrenCaseSensitive(@Attributes int attributes) {
    if (!isDirectory(attributes)) {
      throw new IllegalArgumentException("CHILDREN_CASE_SENSITIVE flag defined for directories only but got file: 0b" + Integer.toBinaryString(attributes));
    }
    if (!isSet(attributes, Flags.CHILDREN_CASE_SENSITIVITY_CACHED)) {
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    return isSet(attributes, Flags.CHILDREN_CASE_SENSITIVE) ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
  }

  public abstract int storeUnlinkedContent(byte @NotNull [] bytes);

  public abstract byte @NotNull [] contentsToByteArray(int contentId) throws IOException;

  public abstract byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file, boolean cacheContent) throws IOException;

  public abstract int acquireContent(@NotNull VirtualFile file);

  public abstract void releaseContent(int contentId);

  public abstract int getCurrentContentId(@NotNull VirtualFile file);

  /** @deprecated bypasses async listeners and is too low-level in general; avoid */
  @Deprecated(forRemoval = true)
  public void processEvents(@NotNull List<? extends @NotNull VFileEvent> events) {
    RefreshQueue.getInstance().processEvents(false, events);
  }

  // 'true' if the FS persisted at least one child, or it has never been queried for children
  public abstract boolean mayHaveChildren(int id);
}
