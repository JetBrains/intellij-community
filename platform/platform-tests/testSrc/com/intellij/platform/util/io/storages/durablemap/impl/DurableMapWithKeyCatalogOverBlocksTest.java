// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the map API over separate key and value records.
public class DurableMapWithKeyCatalogOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  @Test
  public void mapOperationsKeepStableKeysAndSeparateValues(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var map = openStringMap(database);
      assertTrue(map.isEmpty());

      map.put("updated", "first");
      map.put("updated", "second");
      map.put("removed", "value");
      map.remove("removed");
      map.put("FB", "collision-one");
      map.put("Ea", "collision-two");

      assertEquals("second", map.get("updated"));
      assertNull(map.get("removed"));
      assertFalse(map.containsMapping("removed"));
      assertEquals("collision-one", map.get("FB"));
      assertEquals("collision-two", map.get("Ea"));
      assertEquals(3, map.size());

      var keys = new ArrayList<String>();
      assertTrue(map.processKeys(keys::add));
      assertEquals(List.of("updated", "removed", "FB", "Ea"), keys, "Key processing must include the removed key");

      var entries = new LinkedHashMap<String, String>();
      var allEntriesProcessed = map.forEachEntry((key, value) -> {
        entries.put(key, value);
        return true;
      });
      assertTrue(allEntriesProcessed);
      assertEquals(
        new LinkedHashMap<>(java.util.Map.of("updated", "second", "FB", "collision-one", "Ea", "collision-two")),
        entries
      );
    }

    try (var database = factory.open(databaseDirectory)) {
      var map = openStringMap(database);
      assertEquals("second", map.get("updated"));
      assertNull(map.get("removed"));
      assertEquals("collision-one", map.get("FB"));
      assertEquals("collision-two", map.get("Ea"));
      assertEquals(3, map.size());
    }
  }

  @Test
  public void patchChainsStartFromValuesAndFromAnEmptyValue(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    var codec = new AppendStringCodec();
    try (var database = factory.open(databaseDirectory)) {
      var map = openPatchableStringMap(database, codec);

      map.patchValue("key", "a");
      map.patchValue("key", "b");
      assertEquals("ab", map.get("key"));

      map.put("key", "reset");
      map.patchValue("key", "!");
      assertEquals("reset!", map.get("key"));

      map.remove("key");
      map.patchValue("key", "new");
      assertEquals("new", map.get("key"));
      assertEquals(1, map.size());
    }

    try (var database = factory.open(databaseDirectory)) {
      assertEquals("new", openPatchableStringMap(database, codec).get("key"));
    }
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database) throws IOException {
    var descriptor = stringAsUTF8();
    return database.openMap("map", 1, KEY_VALUE_SEPARATELY, descriptor, descriptor);
  }

  private static @NotNull PatchableDurableMap<String, String, String> openPatchableStringMap(
    @NotNull DurableDatabase database,
    @NotNull AppendStringCodec codec
  ) throws IOException {
    return database.openMap("map", 1, KEY_VALUE_SEPARATELY, stringAsUTF8(), codec);
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
