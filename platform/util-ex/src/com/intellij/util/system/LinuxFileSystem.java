// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * File system queries through glibc and {@code libe2p} downcalls. Linux only, 64-bit: {@code struct statfs} starts with a
 * {@code long} on LP64, and the symbols are looked up on the first call.
 */
@ApiStatus.Internal
public final class LinuxFileSystem {
  private static final Logger LOG = Logger.getInstance(LinuxFileSystem.class);

  private LinuxFileSystem() { }

  private static final long BTRFS_SUPER_MAGIC = 0x9123683EL;
  private static final long XFS_SUPER_MAGIC = 0x58465342L;
  private static final long MSDOS_SUPER_MAGIC = 0x4D44L;
  private static final long EXT4_SUPER_MAGIC = 0xEF53L;
  private static final long F2FS_SUPER_MAGIC = 0xF2F52010L;

  /** {@code EXT4_CASEFOLD_FL}, also {@code F2FS_CASEFOLD_FL}: the directory is on a volume with the "casefold" feature, and the inode has it set */
  private static final long EXT4_CASEFOLD_FL = 0x4000_0000L;

  /** {@code struct statfs} is 120 bytes on x86_64 and aarch64; the buffer leaves room for a larger layout. */
  private static final int STATFS_BUFFER_SIZE = 256;

  /**
   * Reads the file system type of {@code path} with {@code statfs}. Btrfs and XFS are case-sensitive, VFAT is not.
   * Ext4 and F2FS support per-directory case folding, so the inode flags decide, when {@code libe2p} is installed.
   *
   * @return the case sensitivity, or {@link FileAttributes.CaseSensitivity#UNKNOWN} for every other file system or on failure
   */
  public static FileAttributes.@NotNull CaseSensitivity caseSensitivity(@NotNull String path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pathSegment = arena.allocateFrom(path);
      MemorySegment buffer = arena.allocate(STATFS_BUFFER_SIZE);
      if ((int)Handles.STATFS.invokeExact(pathSegment, buffer) != 0) {
        if (LOG.isDebugEnabled()) LOG.debug("statfs(" + path + "): error");
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      long type = buffer.get(JAVA_LONG, 0);
      if (type == BTRFS_SUPER_MAGIC || type == XFS_SUPER_MAGIC) {
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      if (type == MSDOS_SUPER_MAGIC) {
        return FileAttributes.CaseSensitivity.INSENSITIVE;
      }
      if (type == EXT4_SUPER_MAGIC || type == F2FS_SUPER_MAGIC) {
        return inodeCaseSensitivity(path, pathSegment, arena);
      }
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static FileAttributes.CaseSensitivity inodeCaseSensitivity(String path, MemorySegment pathSegment, Arena arena) throws Throwable {
    MethodHandle fgetflags = E2pHandles.FGETFLAGS;
    if (fgetflags == null) {
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    MemorySegment flags = arena.allocate(JAVA_LONG);
    if ((int)fgetflags.invokeExact(pathSegment, flags) != 0) {
      if (LOG.isDebugEnabled()) LOG.debug("fgetflags(" + path + "): error");
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    return (flags.get(JAVA_LONG, 0) & EXT4_CASEFOLD_FL) == 0 ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code int statfs(const char *path, struct statfs *buf)}; {@code f_type} is the first field, a {@code long} on LP64 */
    static final MethodHandle STATFS;

    static {
      MemoryLayout nativeLong = LINKER.canonicalLayouts().get("long");
      if (nativeLong.byteSize() != JAVA_LONG.byteSize()) {
        throw new IllegalStateException("Unexpected long: " + nativeLong);
      }
      STATFS = LINKER.downcallHandle(LINKER.defaultLookup().findOrThrow("statfs"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    }
  }

  /** {@code libe2p} ships with e2fsprogs. A system without it answers "unknown" for Ext4 and F2FS. */
  private static final class E2pHandles {
    /** {@code int fgetflags(const char *name, unsigned long *flags)}, or {@code null} when the library is absent */
    static final @Nullable MethodHandle FGETFLAGS = load();

    private static @Nullable MethodHandle load() {
      try {
        SymbolLookup e2p = SymbolLookup.libraryLookup("libe2p.so.2", Arena.global());
        return Linker.nativeLinker().downcallHandle(e2p.findOrThrow("fgetflags"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
      }
      catch (Throwable t) {
        LOG.info("libe2p is unavailable: " + t);
        return null;
      }
    }
  }
}
