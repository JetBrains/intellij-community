// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @ApiStatus.Internal
  public interface Flags {
    //@formatter:off
    int CHILDREN_CACHED                  =           0b0001;
    int IS_DIRECTORY                     =           0b0010;
    int IS_READ_ONLY                     =           0b0100;
    int MUST_RELOAD_CONTENT              =           0b1000;

    int IS_SYMLINK                       =      0b0001_0000;
    int IS_SPECIAL                       =      0b0010_0000;
    int IS_HIDDEN                        =      0b0100_0000;
    int MUST_RELOAD_LENGTH               =      0b1000_0000;
    int CHILDREN_CASE_SENSITIVE          = 0b0001_0000_0000;  // 'true' if this directory can contain files differing only in the case
    int CHILDREN_CASE_SENSITIVITY_CACHED = 0b0010_0000_0000;  // 'true' if this directory's case sensitivity is known
    int FREE_RECORD_FLAG                 = 0b0100_0000_0000;
    int OFFLINE_BY_DEFAULT               = 0b1000_0000_0000;
    //@formatter:on
    static int getAllValidFlags() { return 0xFFF; }
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

  public abstract @NotNull String getName(int id);

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
      throw new IllegalArgumentException(
        "CHILDREN_CASE_SENSITIVE flag defined for directories only but got file: 0b" + Integer.toBinaryString(attributes));
    }
    if (!isSet(attributes, Flags.CHILDREN_CASE_SENSITIVITY_CACHED)) {
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    return isSet(attributes, Flags.CHILDREN_CASE_SENSITIVE)
           ? FileAttributes.CaseSensitivity.SENSITIVE
           : FileAttributes.CaseSensitivity.INSENSITIVE;
  }

  public abstract int storeUnlinkedContent(byte @NotNull [] bytes);

  public abstract byte @NotNull [] contentsToByteArray(int contentId) throws IOException;

  /**
   * Same as {@linkplain #contentsToByteArray(VirtualFile)}, but allows explicitly stating that loaded content should not
   * be put into the VFS content cache
   *
   * @param mayCacheContent {@code true} = caching is allowed (platform may decide itself if caching is needed or not),
   *                        {@code false} = caching is NOT allowed (platform will not cache content).
   */
  public abstract byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file, boolean mayCacheContent) throws IOException;

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
