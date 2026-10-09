// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.openapi.util.Ref;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.RecordStorageOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.SEALED;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.KEYS;
import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.VALUES;
import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DurableMapWithKeyCatalogOverBlocksHousekeeperTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int RECORD_BLOCK_SIZE = 128;
  private static final int PATCH_COUNT = 64;

  @Test
  public void sweepRetiresDeadValuesAndKeepsCurrentSnapshotAndTombstone(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var descriptor = stringAsUTF8();
      try (var map = DurableMapWithKeyCatalogOverBlocks.open(store, RECORD_BLOCK_SIZE, descriptor, descriptor, descriptor)) {
        for (int i = 0; i < 5; i++) {
          map.put("live", largeValue("live-" + i));
        }
        map.put("removed", largeValue("removed"));
        map.remove("removed");
        map.put("sealer", largeValue("sealer"));

        var blockCatalog = DurableMapBlockCatalog.open(store);
        var liveHeadBlockId = RecordStorageOverBlocks.blockId(headForKey(blockCatalog, "live"));
        var tombstoneBlockId = RecordStorageOverBlocks.blockId(headForKey(blockCatalog, "removed"));
        var valueBlocks = blocks(store, VALUES);
        var keyBlockStates = statesOf(blocks(store, KEYS));
        assertEquals(SEALED, requireBlock(store, tombstoneBlockId).state(), "The test tombstone must be in a sealed block");

        var housekeeper = new RetireUnusedBlocksHousekeeper(map);
        housekeeper.runHousekeeping(() -> false); // Schedule the first real round.
        var statesBeforeCancellation = statesOf(valueBlocks);
        housekeeper.runHousekeeping(() -> true);
        assertEquals(statesBeforeCancellation, statesOf(valueBlocks));
        housekeeper.runHousekeeping(() -> false);

        assertTrue(hasBlockInState(valueBlocks, RETIRED), "The sweep must retire dead value blocks");
        assertEquals(SEALED, requireBlock(store, liveHeadBlockId).state(), "The sweep must keep the current snapshot");
        assertEquals(SEALED, requireBlock(store, tombstoneBlockId).state(), "The sweep must keep the current tombstone");
        assertEquals(keyBlockStates, statesOf(blocks(store, KEYS)),
                     "The sweep must not retire key blocks");
        assertTrue(hasBlockInState(blocks(store, VALUES), ACTIVE));
      }
    }
  }

  @Test
  public void sweepKeepsLivePatchChainAndRecoverySkipsDeadPatchChain(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var expected = new StringBuilder("base");
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var codec = new AppendStringCodec();
      var patchableMap = DurableMapWithKeyCatalogOverBlocks.openPatchable(store, RECORD_BLOCK_SIZE, stringAsUTF8(), null, codec);
      try (patchableMap) {
        patchableMap.put("obsolete", "old");
        for (int i = 0; i < PATCH_COUNT; i++) {
          patchableMap.patchValue("obsolete", "+" + i);
        }
        patchableMap.put("obsolete", "replacement");

        patchableMap.put("key", "base");
        for (int i = 0; i < PATCH_COUNT; i++) {
          var patch = "+" + i;
          patchableMap.patchValue("key", patch);
          expected.append(patch);
        }

        var blockCatalog = DurableMapBlockCatalog.open(store);
        var liveHead = headForKey(blockCatalog, "key");
        var valueBlocks = blocks(store, VALUES);
        assertTrue(hasBlockInState(valueBlocks, SEALED), "The patch chain must span sealed blocks");

        var map = (DurableMapWithKeyCatalogOverBlocks<?, ?>)patchableMap;
        var housekeeper = new RetireUnusedBlocksHousekeeper(map);
        housekeeper.runHousekeeping(() -> false);
        housekeeper.runHousekeeping(() -> false);

        var updatedBlockCatalog = DurableMapBlockCatalog.open(store);
        assertEquals(liveHead, headForKey(updatedBlockCatalog, "key"), "The sweep must not replace the live patch chain");
        assertEquals(expected.toString(), patchableMap.get("key"));
        assertEquals("replacement", patchableMap.get("obsolete"));
        assertTrue(hasBlockInState(valueBlocks, RETIRED),
                   "The sweep must retire blocks from the obsolete patch chain");
      }

      var dirtyMap = DurableMapWithKeyCatalogOverBlocks.openPatchable(store, RECORD_BLOCK_SIZE, stringAsUTF8(), null, codec);
      dirtyMap.put("trigger", "value");
      // Close the database without the map. Recovery must ignore orphaned patches whose predecessor block was retired.
    }

    try (var database = factory.open(databaseDirectory);
         var recoveredMap = DurableMapWithKeyCatalogOverBlocks.openPatchable(
           requireMapStore(database), RECORD_BLOCK_SIZE, stringAsUTF8(), null, new AppendStringCodec()
         )) {
      assertEquals(expected.toString(), recoveredMap.get("key"));
      assertEquals("replacement", recoveredMap.get("obsolete"));
      assertEquals("value", recoveredMap.get("trigger"));
    }
  }

  @Test
  public void sweepReleasesMapLockBeforeTraversingValueChains(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var descriptor = stringAsUTF8();
      try (var map = DurableMapWithKeyCatalogOverBlocks.open(store, RECORD_BLOCK_SIZE, descriptor, descriptor, descriptor)) {
        map.put("existing", "value");

        var traversalStarted = new CountDownLatch(1);
        var continueTraversal = new CountDownLatch(1);
        var cancellationChecks = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
          var sweep = executor.submit(() -> {
            map.retireUnusedBlocks(() -> {
              if (cancellationChecks.incrementAndGet() == 4) {
                traversalStarted.countDown();
                await(continueTraversal);
              }
              return false;
            });
            return null;
          });

          assertTrue(traversalStarted.await(10, TimeUnit.SECONDS), "The sweep must start value chain traversal");
          var update = executor.submit(() -> {
            map.put("concurrent", "value");
            return null;
          });
          try {
            update.get(10, TimeUnit.SECONDS);
          }
          finally {
            continueTraversal.countDown();
          }
          sweep.get(10, TimeUnit.SECONDS);
          assertEquals("value", map.get("concurrent"));
        }
        finally {
          executor.shutdownNow();
        }
      }
    }
  }

  private static void await(@NotNull CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out while waiting to continue the sweep");
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting to continue the sweep", e);
    }
  }

  private static long headForKey(@NotNull DurableMapBlockCatalog blockCatalog, @NotNull String expectedKey) throws IOException {
    var keys = RecordStorageOverBlocks.open(
      blockCatalog.appendableRecordBlocks(KEYS),
      /*appendOnly: */ false,
      RECORD_BLOCK_SIZE
    );
    var descriptor = stringAsUTF8();
    var result = new Ref<>(NO_VALUE);
    keys.forEachCommittedRecord((_, record) -> {
      var key = descriptor.read(KeyRecordLayout.keyPayload(record).asByteBuffer());
      if (expectedKey.equals(key)) {
        result.set(KeyRecordLayout.valueRecordRef(record));
        return false;
      }
      return true;
    });
    if (result.get() == NO_VALUE) {
      throw new AssertionError("Missing key " + expectedKey);
    }
    return result.get();
  }

  private static @NotNull List<BlocksStore.Block> blocks(@NotNull BlocksStore store,
                                                          @NotNull DurableMapBlockCatalog.DurableMapBlockRole role) {
    var result = new ArrayList<BlocksStore.Block>();
    for (var block : store.blocks()) {
      if (block.role() == role.persistentCode()) {
        result.add(block);
      }
    }
    return List.copyOf(result);
  }

  private static @NotNull List<BlocksStore.Block.LifecycleState> statesOf(@NotNull List<BlocksStore.Block> blocks) {
    var states = new ArrayList<BlocksStore.Block.LifecycleState>(blocks.size());
    for (var block : blocks) {
      states.add(block.state());
    }
    return List.copyOf(states);
  }

  private static boolean hasBlockInState(@NotNull List<BlocksStore.Block> blocks,
                                         @NotNull BlocksStore.Block.LifecycleState state) {
    for (var block : blocks) {
      if (block.state() == state) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull BlocksStore.Block requireBlock(@NotNull BlocksStore store, int blockId) {
    var block = store.findBlock(blockId);
    if (block == null) {
      throw new AssertionError("Missing block " + blockId);
    }
    return block;
  }

  private static @NotNull BlocksStore requireMapStore(
    @NotNull com.intellij.platform.util.io.storages.database.spi.BlocksDatabase database
  ) {
    var store = database.findStore("map");
    if (store == null) {
      throw new AssertionError("Missing store map");
    }
    return store;
  }

  private static @NotNull String largeValue(@NotNull String prefix) {
    return prefix + "-" + "x".repeat(RECORD_BLOCK_SIZE * 2);
  }

  private static final class AppendStringCodec implements PatchableDurableMap.PatchableValueExternalizer<String, String> {
    private static final byte SET = 1;
    private static final byte APPEND = 2;

    @Override
    public String read(@NotNull ByteBuffer input) throws IOException {
      var result = new StringBuilder();
      while (input.hasRemaining()) {
        var command = input.get();
        if (input.remaining() < Integer.BYTES) {
          throw new IOException("Missing string command length");
        }
        var length = input.getInt();
        if (length < 0 || length > input.remaining()) {
          throw new IOException("Invalid string command length " + length);
        }
        var bytes = new byte[length];
        input.get(bytes);
        var text = new String(bytes, StandardCharsets.UTF_8);
        switch (command) {
          case SET -> {
            result.setLength(0);
            result.append(text);
          }
          case APPEND -> result.append(text);
          default -> throw new IOException("Unknown string command " + command);
        }
      }
      return result.toString();
    }

    @Override
    public @NotNull DataExternalizerEx.KnownSizeRecordWriter writerFor(@NotNull String value) {
      return writer(SET, value);
    }

    @Override
    public @NotNull DataExternalizerEx.KnownSizeRecordWriter writerForPatch(@NotNull String patch) {
      return writer(APPEND, patch);
    }

    private static @NotNull DataExternalizerEx.KnownSizeRecordWriter writer(byte command, @NotNull String text) {
      var textBytes = text.getBytes(StandardCharsets.UTF_8);
      var bytes = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + textBytes.length)
        .put(command)
        .putInt(textBytes.length)
        .put(textBytes)
        .array();
      return DataExternalizerEx.fromBytes(bytes);
    }
  }
}
