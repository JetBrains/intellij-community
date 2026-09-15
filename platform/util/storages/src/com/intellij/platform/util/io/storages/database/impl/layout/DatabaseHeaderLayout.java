// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT16_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT64_UNALIGNED_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;
import static java.nio.ByteOrder.nativeOrder;

/// Defines the persistent layout of the first database metadata record.
///
/// ```
/// DatabaseHeaderLayout[=20 bytes] {
///   magic       : int32
///   formatMajor : int16
///   formatMinor : int16
///   databaseId  : int64
///   chunkSize   : int32
/// }
/// ```
@ApiStatus.Internal
public final class DatabaseHeaderLayout {
  /** The magic-word in the append-only log header */
  public static final int MAGIC = IOUtil.asciiToMagicWord("DMDB");

  public static final short FORMAT_MAJOR = 1;
  public static final short FORMAT_MINOR = 0;


  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("magic"),
    INT16_LAYOUT.withName("formatMajor"),
    INT16_LAYOUT.withName("formatMinor"),
    INT64_UNALIGNED_LAYOUT.withName("databaseId"),
    INT32_LAYOUT.withName("chunkSize")
  ).withName("DatabaseHeaderLayout");

  //@formatter:off
  public static final VarHandle MAGIC_HANDLE        = fieldHandle(LAYOUT, "magic");
  public static final VarHandle FORMAT_MAJOR_HANDLE = fieldHandle(LAYOUT, "formatMajor");
  public static final VarHandle FORMAT_MINOR_HANDLE = fieldHandle(LAYOUT, "formatMinor");
  public static final VarHandle DATABASE_ID_HANDLE  = fieldHandle(LAYOUT, "databaseId");
  public static final VarHandle CHUNK_SIZE_HANDLE   = fieldHandle(LAYOUT, "chunkSize");

  public static final int HEADER_SIZE               = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  private DatabaseHeaderLayout() {}

  public record DatabaseHeader(long databaseId, int chunkSize) {
    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var segment = MemorySegment.ofBuffer(target.slice());
      MAGIC_HANDLE.set(segment, 0L, MAGIC);
      FORMAT_MAJOR_HANDLE.set(segment, 0L, FORMAT_MAJOR);
      FORMAT_MINOR_HANDLE.set(segment, 0L, FORMAT_MINOR);
      DATABASE_ID_HANDLE.set(segment, 0L, databaseId);
      CHUNK_SIZE_HANDLE.set(segment, 0L, chunkSize);
      target.position(target.position() + HEADER_SIZE);
      return target;
    }

    public static @NotNull DatabaseHeader read(@NotNull Path storagePath,
                                               @NotNull ByteBuffer source) throws IOException {
      if (source.remaining() < HEADER_SIZE) {
        throw new CorruptedException(
          "[" + storagePath + "]: database header has " + source.remaining() +
          " bytes, expected at least " + HEADER_SIZE
        );
      }

      var segment = MemorySegment.ofBuffer(source.slice());
      int magicWord = (int)MAGIC_HANDLE.get(segment, 0L);
      if (magicWord != MAGIC) {
        throw new IOException(
          "[" + storagePath + "]: unknown magic word " + IOUtil.magicWordToASCII(magicWord) + ", expected " + MAGIC
        );
      }
      var formatMajor = Short.toUnsignedInt((short)FORMAT_MAJOR_HANDLE.get(segment, 0L));
      var formatMinor = Short.toUnsignedInt((short)FORMAT_MINOR_HANDLE.get(segment, 0L));
      if (formatMajor != FORMAT_MAJOR || formatMinor > FORMAT_MINOR) {
        throw new UnsupportedFormatException(
          "database metadata at " + storagePath,
          FORMAT_MAJOR + "." + FORMAT_MINOR,
          formatMajor + "." + formatMinor
        );
      }

      var databaseId = (long)DATABASE_ID_HANDLE.get(segment, 0L);
      if (databaseId == 0) {
        throw new CorruptedException("[" + storagePath + "]: databaseId must be non-zero");
      }

      var chunkSize = (int)CHUNK_SIZE_HANDLE.get(segment, 0L);
      if (chunkSize <= 0 || Integer.bitCount(chunkSize) != 1) {
        throw new CorruptedException("[" + storagePath + "]: chunkSize(=" + chunkSize + ") must be a positive power of 2");
      }
      return new DatabaseHeader(databaseId, chunkSize);
    }
  }
}
