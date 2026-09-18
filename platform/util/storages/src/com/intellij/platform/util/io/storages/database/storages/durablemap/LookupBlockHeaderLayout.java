// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Common header that identifies the lookup implementation and generation of a `LOOKUP` block.
/// ```
/// LookupBlockHeader[8 bytes] {
///   implementationId: int32,  // =Hash | BTree | ...
///   generation      : int32
/// }
/// ```
final class LookupBlockHeaderLayout {
  private static final MemoryLayout HEADER_LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("implementationId"),
    INT32_LAYOUT.withName("generation")
  ).withName("LookupBlockHeaderLayout");

  //formatter:off
  private static final VarHandle IMPLEMENTATION_ID_HANDLE = fieldHandle(HEADER_LAYOUT, "implementationId");
  private static final VarHandle GENERATION_HANDLE        = fieldHandle(HEADER_LAYOUT, "generation");

  static final int          HEADER_SIZE                   = Math.toIntExact(HEADER_LAYOUT.byteSize());
  //formatter:on

  static void initialize(@NotNull BlocksStore.Block block, int implementationId, int generation) {
    checkIdentity(implementationId, generation);
    var content = block.content();
    if (content.byteSize() < HEADER_SIZE) {
      throw new IllegalArgumentException("Block " + block.id() + " is too small for a lookup header");
    }
    IMPLEMENTATION_ID_HANDLE.set(content, 0L, implementationId);
    GENERATION_HANDLE.set(content, 0L, generation);
  }

  static int implementationId(@NotNull BlocksStore.Block block) throws CorruptedException {
    var content = validatedContent(block);
    var implementationId = (int)IMPLEMENTATION_ID_HANDLE.get(content, 0L);
    if (implementationId <= 0) {
      throw corrupted(block, "implementation id " + implementationId + " is not positive");
    }
    return implementationId;
  }

  static int generation(@NotNull BlocksStore.Block block) throws CorruptedException {
    var content = validatedContent(block);
    var generation = (int)GENERATION_HANDLE.get(content, 0L);
    if (generation < 0) {
      throw corrupted(block, "generation " + generation + " is negative");
    }
    return generation;
  }

  static @NotNull MemorySegment payload(@NotNull BlocksStore.Block block) throws CorruptedException {
    return validatedContent(block).asSlice(HEADER_SIZE);
  }

  private static @NotNull MemorySegment validatedContent(@NotNull BlocksStore.Block block) throws CorruptedException {
    var content = block.content();
    if (content.byteSize() < HEADER_SIZE) {
      throw corrupted(block, "content is too small for a lookup header");
    }
    return content;
  }

  private static void checkIdentity(int implementationId, int generation) {
    if (implementationId <= 0) {
      throw new IllegalArgumentException("implementationId(=" + implementationId + ") must be positive");
    }
    if (generation < 0) {
      throw new IllegalArgumentException("generation(=" + generation + ") must not be negative");
    }
  }

  private static @NotNull CorruptedException corrupted(@NotNull BlocksStore.Block block, @NotNull String details) {
    return new CorruptedException("Invalid lookup block #" + block.id() + ": " + details);
  }

  private LookupBlockHeaderLayout() { }
}
