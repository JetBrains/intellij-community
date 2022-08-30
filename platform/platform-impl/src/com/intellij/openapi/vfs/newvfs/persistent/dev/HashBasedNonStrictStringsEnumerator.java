// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Non-strict String enumerator: i.e. id of given string is not unique, it could be same string assigned
 * several ids, but each such id maps back to that string (same value, but not always same object!)
 *
 * FIXME RC: this implementation is sliiiightly more efficient than OffsetBased, but it has
 *           much more downsides, most important one is quite low limit of the amount of names
 *           to store. So probably it doesn't worth to be investigated further.
 */
public class HashBasedNonStrictStringsEnumerator implements DataEnumerator<String>, AutoCloseable {

  public static final int UNDEFINED_ID = Integer.MIN_VALUE;

  //ID = (bucketNoInFile:22)|(valueHash:10)
  //   22bits for bucketNo = 4Mil buckets to address, hence <= 4M strings to store
  //
  private static final int HASH_BITS_IN_ID = 10;
  private static final int HASH_BITS_MASK = 0b11_1111_1111;
  private static final int MAX_BUCKET = 1 << (Integer.SIZE - HASH_BITS_IN_ID) - 1;

  /**
   * bucketSize * totalBuckets(1<<22) = max storable
   */
  private static final int DISK_BUCKET_SIZE = 16;

  private static final int CACHE_SIZE = 1 << HASH_BITS_IN_ID;

  private final String[] recentNames = new String[CACHE_SIZE];
  private final int[] recentIds = new int[CACHE_SIZE];

  private int nextBucketToUse = 0;
  private final ResizeableMappedFile file;

  public HashBasedNonStrictStringsEnumerator(final @NotNull ResizeableMappedFile file) throws IOException {
    this.file = file;
    Arrays.fill(recentIds, UNDEFINED_ID);


    final long fileSize = file.getLogicalSize();
    if (fileSize == 0) {
      nextBucketToUse = 0;
    }
    else {
      nextBucketToUse = (int)((fileSize / DISK_BUCKET_SIZE) + 1);
      //RC: do we really need to read through whole file? We could read nothing, or read last 2-3 CACHE_SIZE
      //    records to fill up recentNames/Ids cache.
      //    We can't read file backward that with <length> field _before_ string bytes, but we could
      //    put <length> _after_ string bytes, and file could be read backward this way
      final int[] offsetRef = {0};
      while (true) {
        final int offset = offsetRef[0];
        if (offset >= fileSize) {
          break;
        }
        final String value = readValueByOffset(offsetRef);
        final int hashIndex = value.hashCode() & HASH_BITS_MASK;
        final int bucket = offset / DISK_BUCKET_SIZE;
        final int id = (bucket << HASH_BITS_IN_ID) + hashIndex;
        recentNames[hashIndex] = value;
        recentIds[hashIndex] = id;

        offsetRef[0] = offsetRef[0] % DISK_BUCKET_SIZE == 0 ?
                       offsetRef[0] :
                       (offsetRef[0] / DISK_BUCKET_SIZE + 1) * DISK_BUCKET_SIZE;
      }
    }

    //TODO RC: put header(version, ?) first
  }

  @Override
  public int enumerate(final @Nullable String value) throws IOException {
    final int hashIndex = value.hashCode() & HASH_BITS_MASK;
    if (Objects.equals(value, recentNames[hashIndex])) {
      return recentIds[hashIndex];
    }
    else {
      if (nextBucketToUse > MAX_BUCKET) {
        throw new IOException("Overflow: bucket(=" + nextBucketToUse + ") > MAX(=" + MAX_BUCKET + ") -> storage capacity exhausted");
      }
      recentNames[hashIndex] = value;
      final int id = (nextBucketToUse << HASH_BITS_IN_ID) + hashIndex;
      recentIds[hashIndex] = id;

      final long offset = (long)nextBucketToUse * DISK_BUCKET_SIZE;
      final byte[] valueBytes = value.getBytes(UTF_8);
      final int valueLength = valueBytes.length;
      final int lengthToStore = valueLength + Integer.BYTES; // + length field
      final int bucketsToOccupy = (lengthToStore % DISK_BUCKET_SIZE > 0) ?
                                  lengthToStore / DISK_BUCKET_SIZE + 1 :
                                  lengthToStore / DISK_BUCKET_SIZE;
      file.lockWrite();
      try {
        file.putInt(offset, valueLength);
        file.put(offset + Integer.BYTES, valueBytes, 0, valueLength);
      }
      finally {
        file.unlockWrite();
      }

      nextBucketToUse += bucketsToOccupy;
      return id;
    }
  }

  @Override
  public @Nullable String valueOf(final int id) throws IOException {
    final int hashIndex = hashFromId(id);
    if (recentIds[hashIndex] == id) {
      return recentNames[hashIndex];
    }

    final String valueFromDisk = loadValueById(id);
    recentNames[hashIndex] = valueFromDisk;
    recentIds[hashIndex] = id;

    return valueFromDisk;
  }

  @Override
  public void close() throws Exception {
    file.close();
  }

  private String loadValueById(final int id) throws IOException {
    final int bucket = bucketById(id);
    final int offset = bucket * DISK_BUCKET_SIZE;
    return readValueByOffset(new int[]{offset});
  }

  @NotNull
  private String readValueByOffset(/*InOut*/final int[] offsetRef) throws IOException {
    file.lockRead();
    try {
      final int offset = offsetRef[0];

      final int stringLengthOffset = offset + Integer.BYTES;
      if (file.getLogicalSize() < stringLengthOffset) {
        throw new IOException("offset(" + stringLengthOffset + ") is out of file(length=" + file.getLogicalSize() + ") " +
                              "-> id is incorrect, or file was corrupted");
      }
      final int length = file.getInt(offset);
      if (file.getLogicalSize() < stringLengthOffset + length) {
        throw new IOException(
          "offset(" + (stringLengthOffset + length) + ") is out of file(length=" + file.getLogicalSize() + ") " +
          "-> id is incorrect, or file was corrupted");
      }
      final byte[] bytes = new byte[length];
      file.get(stringLengthOffset, bytes, 0, length, false);
      offsetRef[0] = stringLengthOffset + length;
      return new String(bytes, UTF_8);
    }
    finally {
      file.unlockRead();
    }
  }


  private static int hashFromId(final int id) {
    return id & HASH_BITS_MASK;
  }

  private static int bucketById(final int id) {
    return id >> HASH_BITS_IN_ID;
  }

  public static void main(String[] args) throws IOException {
    final File filenamesTxt = new File("./names.txt");
    final File namesFile = new File("./names.names");
    namesFile.delete();

    //RC: approximately 2-3 times faster, but approximately 2.2 times larger file

    //TODO RC: is it possible to keep the idea of hash-addressed cache, but make that cache-table
    //         segmented, so that 'old' segments could be atomically cut off, and store to the disk?

    final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      namesFile.toPath(),
      10 * PagedFileStorage.MB,
      null,
      PagedFileStorage.MB,
      true
    );
    final HashBasedNonStrictStringsEnumerator enumerator = new HashBasedNonStrictStringsEnumerator(mappedFile);
    //final PersistentCharSequenceEnumerator enumerator = new PersistentCharSequenceEnumerator(namesFile.toPath(), 1024);

    final List<String> paths = Files.readAllLines(filenamesTxt.toPath());
    final long startedAtNs = System.nanoTime();
    long pureLength = 0;
    for (String path : paths) {
      final String name = new File(path).getName();
      enumerator.enumerate(name);

      pureLength += name.length();
    }
    System.out.println(TimeoutUtil.getDurationMillis(startedAtNs) + " ms for " + paths.size() + " names");
    System.out.println("Pure length: " + pureLength + " bytes");
  }
}
