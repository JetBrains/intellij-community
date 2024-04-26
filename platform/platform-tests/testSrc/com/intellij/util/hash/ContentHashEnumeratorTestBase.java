// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.hash;

import com.intellij.openapi.util.io.ByteArraySequence;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ContentHashEnumeratorTestBase {

  private static final int ENOUGH_HASHES = 1 << 20;

  private ContentHashEnumerator enumerator;

  protected abstract ContentHashEnumerator openEnumerator() throws IOException;

  @BeforeEach
  void setUp() throws IOException {
    enumerator = openEnumerator();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (enumerator != null) {
      enumerator.closeAndClean();
    }
  }

  @Test
  void enumeratorAssignsConsequentIds_ToUniqueHashes() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      int id = enumerator.enumerate(hash.toBytes());
      assertEquals(
        i + 1,
        id,
        "[" + i + "] .enumerate() must return consequent id"
      );
      assertEquals(
        i + 1,
        enumerator.recordsCount(),
        "[" + i + "] .recordsCount() must be equals to number of hashes enumerated before"
      );
    }
  }

  @Test
  void valueOf_restoresValueEnumerated() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      int id = enumerator.enumerate(hash.toBytes());
      byte[] restoredHash = enumerator.valueOf(id);
      assertArrayEquals(
        hash.toBytes(),
        restoredHash,
        "[" + i + "] .valueOf(enumerate(hash)) must return same hash"
      );
    }
  }

  @Test
  void enumerateEx_IsSameAsEnumerate_ForNewHashes() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      int enumeratedExId = enumerator.enumerateEx(hash.toBytes());
      int enumeratedId = enumerator.enumerate(hash.toBytes());
      assertEquals(
        enumeratedId,
        enumeratedExId,
        "[" + i + "] .enumerateEx() for new hashes must return same id as .enumerate()"
      );
    }
  }

  @Test
  void enumerateEx_ReturnsSameIdAsEnumerateButNegative_ForAlreadyKnownHashes() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      int enumeratedId = enumerator.enumerate(hash.toBytes());
      int enumeratedExId = enumerator.enumerateEx(hash.toBytes());
      assertEquals(
        -enumeratedId,
        enumeratedExId,
        "[" + i + "] .enumerateEx() for _known_ hashes must return -id returned by .enumerate()"
      );
    }
  }

  @Test
  void tryEnumerate_ReturnsSameIdAsEnumerate_ForAlreadyKnownHashes() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      assertEquals(
        enumerator.enumerate(hash.toBytes()),
        enumerator.tryEnumerate(hash.toBytes()),
        "[" + i + "] .tryEnumerate() for _known_ hashes must return same as .enumerate()"
      );
    }
  }

  @Test
  void allEnumeratedHashes_ListedWithForEach() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    int[] hashIds = new int[uniqueHashes.length];
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      hashIds[i] = enumerator.enumerate(hash.toBytes());
    }

    Int2ObjectMap<byte[]> iteratedIdToHash = new Int2ObjectOpenHashMap<>();
    enumerator.forEach((hashId, hash) -> {
      iteratedIdToHash.put(hashId, hash);
      return true;
    });

    assertEquals(
      uniqueHashes.length,
      iteratedIdToHash.size(),
      ".forEach must iterate through same hashes count as were .enumerate()-ed before"
    );

    for (int i = 0; i < hashIds.length; i++) {
      int enumeratedId = hashIds[i];
      ByteArraySequence enumeratedHash = uniqueHashes[i];
      byte[] iteratedHash = iteratedIdToHash.get(enumeratedId);
      assertArrayEquals(
        enumeratedHash.toBytes(),
        iteratedHash,
        "[" + i + "][#" + enumeratedId + "]: hash delivered via .forEach() must be same as enumerate()-ed before"
      );
    }
  }


  @Test
  void allEnumeratedHashes_ListedWithForEach_AfterCloseAndReopen() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteArraySequence[] uniqueHashes = generateUniqueHashes(rnd, ENOUGH_HASHES);
    int[] hashIds = new int[uniqueHashes.length];
    for (int i = 0; i < uniqueHashes.length; i++) {
      ByteArraySequence hash = uniqueHashes[i];
      hashIds[i] = enumerator.enumerate(hash.toBytes());
    }

    assertEquals(
      uniqueHashes.length,
      enumerator.recordsCount()
    );

    enumerator.close();
    enumerator = openEnumerator();

    assertEquals(
      uniqueHashes.length,
      enumerator.recordsCount(),
      "Reopened enumerator must have same recordsCount as it had before close()-ing"
    );

    Int2ObjectMap<byte[]> iteratedIdToHash = new Int2ObjectOpenHashMap<>();
    enumerator.forEach((hashId, hash) -> {
      iteratedIdToHash.put(hashId, hash);
      return true;
    });

    assertEquals(
      uniqueHashes.length,
      iteratedIdToHash.size(),
      ".forEach must iterate through same hashes count as were .enumerate()-ed before"
    );

    for (int i = 0; i < hashIds.length; i++) {
      int enumeratedId = hashIds[i];
      ByteArraySequence enumeratedHash = uniqueHashes[i];
      byte[] iteratedHash = iteratedIdToHash.get(enumeratedId);
      assertArrayEquals(
        enumeratedHash.toBytes(),
        iteratedHash,
        "[" + i + "][#" + enumeratedId + "]: hash delivered via .forEach() must be same as enumerate()-ed before"
      );
    }
  }

  protected static ByteArraySequence[] generateUniqueHashes(@NotNull ThreadLocalRandom rnd,
                                                            int size) {
    return uniqueHashesStream(rnd, size)
      .toArray(ByteArraySequence[]::new);
  }


  private static @NotNull Stream<ByteArraySequence> uniqueHashesStream(@NotNull ThreadLocalRandom rnd,
                                                                       int size) {
    return IntStream.iterate(0, i -> i + 1)
      .mapToObj(i -> randomHash(rnd))
      .distinct()
      .limit(size);
  }

  protected static ByteArraySequence randomHash(final Random rnd) {
    final byte[] hash = new byte[ContentHashEnumerator.SIGNATURE_LENGTH];
    for (int i = 0; i < hash.length; i++) {
      hash[i] = (byte)rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
    }
    return new ByteArraySequence(hash);
  }
}