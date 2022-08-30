// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Non-strict String enumerator: i.e. id of given string is not unique, it could be same string assigned
 * several ids, but each such id maps back to that string (same value, but not always same object!)
 * <p>
 * This implementation stores name in a file (record: [length, UTF8 bytes]), and id is the offset of the
 * record. On the top of that uses probabilistic MRU caches for most recent id/values resolution.
 */
public class OffsetBasedNonStrictStringsEnumerator implements DataEnumerator<String>, AutoCloseable {

  public static final int UNDEFINED_ID = Integer.MIN_VALUE;

  private static final int CACHE_SIZE = 1 << 14;
  private static final int MAX_PROBES = 5;

  private final RandomReplacementIntToStringCache idsToNames = new RandomReplacementIntToStringCache();
  private final RandomReplacementStringToIntCache namesToIds = new RandomReplacementStringToIntCache();

  private int nextOffsetToUse = 0;
  private final ResizeableMappedFile file;

  public OffsetBasedNonStrictStringsEnumerator(final @NotNull ResizeableMappedFile file) throws IOException {
    this.file = file;

    //TODO RC: put header(version, ?) first

    final long fileSize = file.getLogicalSize();
    nextOffsetToUse = 0;
    while (nextOffsetToUse < fileSize) {
      //RC: do we really need to read through whole file? We could read nothing, or read last 2-3 CACHE_SIZE
      //    records to fill up recentNames/Ids cache.
      //    We can't read file backward that with <length> field _before_ string bytes, but we could
      //    put <length> _after_ string bytes, and file could be read backward this way

      final String value = valueOf(nextOffsetToUse);
      nextOffsetToUse += Integer.BYTES + value.getBytes(UTF_8).length;
    }
  }

  @Override
  public int enumerate(final @Nullable String value) throws IOException {
    return namesToIds.lookup(value, () -> {
      final int offset = nextOffsetToUse;
      final byte[] stringBytes = value.getBytes(UTF_8);
      final int stringLength = stringBytes.length;
      final int fullRecordLength = stringLength + Integer.BYTES; // + length field
      file.lockWrite();
      try {
        file.putInt(offset, stringLength);
        file.put(offset + Integer.BYTES, stringBytes, 0, stringLength);
      }
      finally {
        file.unlockWrite();
      }

      nextOffsetToUse += fullRecordLength;
      return offset;
    });
  }

  @Override
  public @Nullable String valueOf(final int id) throws IOException {
    return idsToNames.lookup(id, () -> {
      final int offset = id;
      if (offset + Integer.BYTES > file.getLogicalSize()) {
        throw new IOException("id[" + id + "]: record offset(" + offset + ", +" + Integer.BYTES + "b) " +
                              "is outside of file[0," + file.getLogicalSize() + ") " +
                              "-> incorrect id or file[" + file + "] is corrupted");
      }
      final int stringLength = file.getInt(offset);

      final int fullRecordLength = Integer.BYTES + stringLength;
      if (offset + fullRecordLength > file.getLogicalSize()) {
        throw new IOException("id[" + id + "]: record(" + offset + ", +" + fullRecordLength + "b) " +
                              "is outside of file[0," + file.getLogicalSize() + ") " +
                              "-> incorrect id or file[" + file + "] is corrupted");
      }
      final byte[] stringBytes = new byte[stringLength];
      file.get(offset + Integer.BYTES, stringBytes, 0, stringLength, false);
      return new String(stringBytes, UTF_8);
    });
  }

  @Override
  public void close() throws Exception {
    file.close();
  }

  /**
   * Uses fixed-size open-addressing linear-probing hashmap for storing associations. If key is not
   * found, its value is computed, and (key,value) inserted into a map into a free slot (if remains),
   * or, if all slots are occupied, random slot is chosen among probing slots, and replaced. This
   * gives probabilistically kind-of-MRU (old values are likely die out, fresh values are likely
   * cached), but without usage counters/timestamps.
   */
  public static class RandomReplacementStringToIntCache {
    public static final int SIZE = CACHE_SIZE;
    public static final int MAX_PROBES = OffsetBasedNonStrictStringsEnumerator.MAX_PROBES;

    private final String[] keys = new String[SIZE];
    private final int[] values = new int[SIZE];

    public int lookup(final @NotNull String key,
                      final IntThrowableComputable<IOException> orCompute) throws IOException {
      final int hash = key.hashCode();
      final int startIndex = Math.abs(hash);
      for (int probe = 0; probe < MAX_PROBES; probe++) {
        final int index = (startIndex + probe) % SIZE;
        final String candidateKey = keys[index];
        if (candidateKey == null) {//free space still available -> just utilize it for new value
          final int newValue = orCompute.compute();
          keys[index] = key;
          values[index] = newValue;
          return newValue;
        }
        else if (key.equals(candidateKey)) {
          return values[index];
        }
      }

      final int newValue = orCompute.compute();
      //no more free space remains, need to expurge some record -> choose the random one
      final int probe = ThreadLocalRandom.current().nextInt(MAX_PROBES);
      final int index = (startIndex + probe) % SIZE;
      keys[index] = key;
      values[index] = newValue;
      return newValue;
    }
  }

  /**
   * See {@link RandomReplacementStringToIntCache}
   */
  public static class RandomReplacementIntToStringCache {
    public static final int SIZE = CACHE_SIZE;
    public static final int MAX_PROBE = MAX_PROBES;

    private final int[] keys = new int[SIZE];
    private final String[] values = new String[SIZE];

    {
      Arrays.fill(keys, UNDEFINED_ID);
    }

    public String lookup(final @NotNull int key,
                         final ThrowableComputable<String, IOException> orCompute) throws IOException {
      final int hash = Integer.hashCode(key);
      final int startIndex = Math.abs(hash);
      for (int probe = 0; probe < MAX_PROBE; probe++) {
        final int index = (startIndex + probe) % SIZE;
        final int candidateKey = keys[index];
        if (candidateKey == UNDEFINED_ID) {//free space still available -> just utilize it for new value
          final String newValue = orCompute.compute();
          keys[index] = key;
          values[index] = newValue;
          return newValue;
        }
        else if (key == candidateKey) {
          return values[index];
        }
      }

      final String newValue = orCompute.compute();
      //no more free space remains, need to expurge some record -> choose the random one
      final int probe = ThreadLocalRandom.current().nextInt(MAX_PROBE);
      final int index = (startIndex + probe) % SIZE;
      keys[index] = key;
      values[index] = newValue;
      return newValue;
    }
  }

  private interface IntThrowableComputable<E extends Exception> {
    public int compute() throws E;
  }


  public static void main(String[] args) throws IOException {
    final File filenamesTxt = new File("./names.txt");
    final File namesFile = new File("./names.names");
    namesFile.delete();

    //RC: approximately 2-3 times faster, but approximately 2.2 times larger file

    final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      namesFile.toPath(),
      10 * PagedFileStorage.MB,
      null,
      PagedFileStorage.MB,
      false
    );
    final OffsetBasedNonStrictStringsEnumerator enumerator = new OffsetBasedNonStrictStringsEnumerator(mappedFile);
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
