// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY;
import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the public lifecycle of named durable maps. */
public class DurableDatabaseTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  @Test
  public void openMapReturnsTheOpenInstanceForTheSameName(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var first = openStringMap(database, 1);
      var second = openStringMap(database, 1);

      assertSame(first, second);
      first.put("key", "value");
      assertEquals("value", second.get("key"));
    }
  }

  @Test
  public void oldOverloadUsesKeyValueTogether(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var implicit = openStringMap(database, 1);
      var explicit = openStringMap(database, KEY_VALUE_TOGETHER);

      assertSame(implicit, explicit, "The old overload must select the existing map implementation");
    }
  }

  @Test
  public void selectedImplementationsCreateIndependentMaps(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var together = openStringMap(database, "together", KEY_VALUE_TOGETHER);
      var separately = openStringMap(database, "separately", KEY_VALUE_SEPARATELY);
      together.put("key", "together");
      separately.put("key", "separately");

      assertEquals("together", together.get("key"));
      assertEquals("separately", separately.get("key"));
      assertEquals(List.of("together", "separately"), database.mapNames());
    }
  }

  @Test
  public void openMapCacheRejectsAnotherImplementation(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var map = openStringMap(database, KEY_VALUE_TOGETHER);

      assertThrows(
        UnsupportedFormatException.class,
        () -> openStringMap(database, KEY_VALUE_SEPARATELY)
      );
      assertSame(map, openStringMap(database, 1), "A rejected selector must preserve the cached map");
    }
  }

  @Test
  public void metadataSelectsKeyValueTogether(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      openStringMap(database, 1).put("key", "value");
    }

    try (var database = factory.open(databaseDirectory)) {
      assertThrows(
        UnsupportedFormatException.class,
        () -> openStringMap(database, KEY_VALUE_SEPARATELY)
      );
      assertEquals(
        "value",
        openStringMap(database, KEY_VALUE_TOGETHER).get("key"),
        "A rejected selector must not change metadata or map data"
      );
    }
  }

  @Test
  public void metadataSelectsKeyValueSeparately(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      openStringMap(database, KEY_VALUE_SEPARATELY).put("key", "value");
    }

    try (var database = factory.open(databaseDirectory)) {
      assertThrows(
        UnsupportedFormatException.class,
        () -> openStringMap(database, KEY_VALUE_TOGETHER)
      );
      assertEquals("value", openStringMap(database, KEY_VALUE_SEPARATELY).get("key"));
    }
  }

  @Test
  public void openMapRejectsAnotherDataVersion(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      openStringMap(database, 1);

      assertThrows(UnsupportedFormatException.class, () -> openStringMap(database, 2));
    }
  }

  @Test
  public void closedMapCanOpenAgainOverTheSameStore(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var first = openStringMap(database, 1);
      first.put("key", "value");
      first.close();

      var reopened = openStringMap(database, 1);

      assertNotSame(first, reopened);
      assertEquals("value", reopened.get("key"));
    }
  }

  @Test
  public void mapOpensAfterTheDatabaseRestarts(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      openStringMap(database, 1).put("key", "value");
    }

    try (var database = factory.open(databaseDirectory)) {
      assertEquals("value", openStringMap(database, 1).get("key"));
    }
  }

  @Test
  public void cleanedMapCanOpenAsAnEmptyReplacement(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var first = openStringMap(database, 1);
      first.put("key", "value");
      first.closeAndClean();

      var replacement = openStringMap(database, 1);

      assertNotSame(first, replacement);
      assertTrue(replacement.isEmpty());
    }
  }

  @Test
  public void mapNamesReturnsAStableSnapshot(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      openStringMap(database, "first", 1);
      var snapshot = database.mapNames();
      openStringMap(database, "second", 1);

      assertEquals(List.of("first"), snapshot);
      assertEquals(List.of("first", "second"), database.mapNames());
    }
  }

  @Test
  public void droppingAMissingMapDoesNotCreateIt(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      database.dropMap("missing");

      assertTrue(database.mapNames().isEmpty(), "Dropping a missing map must not register a store");
    }
  }

  @Test
  public void droppingAnOpenMapIsRejected(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      openStringMap(database, 1);

      assertThrows(IllegalStateException.class, () -> database.dropMap("map"));
      assertEquals(List.of("map"), database.mapNames(), "A rejected drop must preserve the map");
    }
  }

  @Test
  public void droppingAClosedMapRemovesItsDataAndVersion(@TempDir Path databaseDirectory) throws Exception {
    var factory = new DurableDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var map = openStringMap(database, 1);
      map.put("key", "value");
      map.close();

      database.dropMap("map");

      assertTrue(database.mapNames().isEmpty());
    }

    try (var database = factory.open(databaseDirectory)) {
      assertTrue(database.mapNames().isEmpty(), "The dropped map must stay absent after a database restart");
      assertTrue(openStringMap(database, 2).isEmpty(), "The drop must remove the old data version without opening its data");
    }
  }

  @Test
  public void databaseCloseClosesItsOpenMaps(@TempDir Path databaseDirectory) throws Exception {
    var database = new DurableDatabaseFactory(CHUNK_SIZE).open(databaseDirectory);
    var map = openStringMap(database, 1);

    database.close();

    assertTrue(map.isClosed());
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database,
                                                                   int dataVersion) throws IOException {
    return openStringMap(database, "map", dataVersion);
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database,
                                                                   @NotNull String name,
                                                                   int dataVersion) throws IOException {
    KeyDescriptorEx<String> descriptor = stringAsUTF8();
    return database.openMap(name, dataVersion, descriptor, descriptor);
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database,
                                                                   @NotNull DurableMapImplementation implementation) throws IOException {
    return openStringMap(database, "map", implementation);
  }

  private static @NotNull DurableMap<String, String> openStringMap(@NotNull DurableDatabase database,
                                                                   @NotNull String name,
                                                                   @NotNull DurableMapImplementation implementation) throws IOException {
    KeyDescriptorEx<String> descriptor = stringAsUTF8();
    return database.openMap(name, 1, implementation, descriptor, descriptor);
  }
}
