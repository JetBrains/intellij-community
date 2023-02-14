// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.ResizeableMappedFile;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Non-strict String enumerator: i.e. id of given string is not unique, it could be same string assigned
 * several ids, but each such id maps back to that string (same value, but not always same object!)
 * <p>
 * This implementation stores name in a file (record: [length, UTF8 bytes]), and id is the offset of the
 * record. On the top of that uses probabilistic MRU caches for most recent id/values resolution.
 * <p>
 * Not thread safe -- external synchronization needed if used in multithreaded environment
 */
public class OffsetBasedNonStrictStringsEnumerator implements ScannableDataEnumeratorEx<String>, Forceable, Closeable {

  //RC: I've tested implementation against PersistentStringEnumerator as a baseline, on a list of names
  //    from a project tree:
  //      > find . -print > filepaths.txt
  //      > cat filepaths.txt | sed 's#.*/##' > names.txt
  //    (see main() for code). Results are following:
  //
  //    PersistentStringEnumerator:
  //      1159 ms for .enumerate()-ing 1_900_994 names (50_938084 chars)
  //      Names stored: 880_154, out of 1_900_994 (46 %)
  //      Stored file(s) size: 45_191_192 bytes
  //
  //    OffsetBasedNonStrictStringsEnumerator:
  //      386 ms for .enumerate()-ing 1_900_994 names (50_938084 chars)
  //      Names stored: 1_334_552, out of 1_900_994 (70 %)
  //      Stored file(s) size: 73_146_376 bytes
  //
  //    Basically, this enumerator is ~3 times faster (on .enumerate()), but stores 25% more strings,
  //    (70% vs 46%) i.e. it eliminates only ~1/2 of the duplicates strict Enumerator eliminates
  //    (30% vs 56%).


  /**
   * Cache size is better to be power of 2, so mod/div operations could be implemented by bitwise ops.
   * Modern JIT does it automatically, but for that divisor should be compile-time constant,
   * so javac could inline it, and JIT know its value statically. Without it div/mod ops should be
   * manually converted to shift/mask, which makes code a bit less readable.
   */
  private static final int CACHE_SIZE = 1 << 14;
  private static final int MAX_PROBES = 5;


  /* ============================ Persistent format: ========================================================================= */

  //Persistent format:
  //  header(version: 4 bytes, file status: 4 bytes)
  //  record(stringLength: 4 bytes, redirectToId: 4 bytes, stringBytes[stringLength])*

  public static final int VERSION = 1;

  private static final int HEADER_VERSION_OFFSET = 0;
  private static final int HEADER_SAFE_CLOSE_OFFSET = HEADER_VERSION_OFFSET + Integer.BYTES;
  private static final int HEADER_SIZE = HEADER_SAFE_CLOSE_OFFSET + Integer.BYTES;

  private static final int RECORD_STRING_LENGTH_OFFSET = 0;
  private static final int RECORD_REDIRECT_ID_OFFSET = Integer.BYTES;
  private static final int RECORD_HEADER_SIZE = RECORD_REDIRECT_ID_OFFSET + Integer.BYTES;

  //TODO RC: deduplication/compaction: each record has a field for 'redirectToId', which used by
  //         compaction algorithms to link duplicates together. Such deduplication could be done offline,
  //         or incrementally, in idle time, by scanning storage and linking duplicates found.
  //         How to deduplicate: additional method like 'valueOf(id, redirectToId[]), which resolves
  //         id and provide redirectId, if any. Also caches tries to cache redirectToId instead/in addition
  //         to original one. Fast accesses still could go through valueOf(id), but not-so-fast accesses
  //         could use valueOf(id, redirectTo), and 'fix' an id in a main table.
  //         Compaction: flag 'unused record' is needed to compaction, and after that compaction could
  //         be done by just copying all 'used' records to a new storage, with redirect-to ids used to

  /* ================================================================================================================ */


  private final RandomReplacementIntToStringCache idsToNames = new RandomReplacementIntToStringCache();
  private final RandomReplacementStringToIntCache namesToIds = new RandomReplacementStringToIntCache();

  private int nextOffsetToUse;
  private final ResizeableMappedFile file;

  //monitoring:
  private volatile long valueOfQueries = 0;
  private volatile long valueOfQueriesCacheMisses = 0;
  private volatile long enumerateQueries = 0;
  private volatile long enumerateQueriesCacheMisses = 0;
  @Nullable
  private volatile BatchCallback monitoringCallback;

  public OffsetBasedNonStrictStringsEnumerator(final @NotNull ResizeableMappedFile file) throws IOException {
    this.file = file;

    final long fileSize = file.getLogicalSize();
    file.lockWrite();
    try {
      if (fileSize >= HEADER_SIZE) {
        final int version = file.getInt(HEADER_VERSION_OFFSET);
        if (version != VERSION) {
          throw new IOException("Enumerator persistent format version (" + VERSION + ") != read version (" + version + ")");
        }
        final int safeClosedMarker = file.getInt(HEADER_SAFE_CLOSE_OFFSET);
        //TODO check it against magic value
      }
      else {

        file.putInt(HEADER_VERSION_OFFSET, VERSION);
        file.putInt(HEADER_SAFE_CLOSE_OFFSET, 0); //TODO put value 'opened'
      }

      //TODO RC: do we really need to read through whole file? We could read nothing (leaving cache empty, but
      //    this is OK from correctness PoV), or read last 2-3 PAGE_SIZE records to fill up recentNames/Ids cache.
      //    ...But we can't read file backward that with <length> field _before_ string bytes, so for that
      //    to work we need to put <length> _after_ string bytes -- which makes it hard to read file in a
      //    regular start->end way.
      //    Both issues could be solved by storing data page-aware: i.e. never put record on a page boundary,
      //    move record on a new page if it doesn't fit on a current page fully. This way page always start
      //    with record x .length field, hence it is enough to scan few last pages to fill up the cache. This also
      //    should slightly improve performance of ResizeableMappedFile (see valuesAreBufferAligned ctor param)

      nextOffsetToUse = HEADER_SIZE;
      while (nextOffsetToUse < fileSize) {
        final String value = valueOf(nextOffsetToUse);
        nextOffsetToUse += value.getBytes(UTF_8).length + RECORD_HEADER_SIZE;
      }
    }
    finally {
      file.unlockWrite();
    }
  }

  @Override
  public int enumerate(final @Nullable String value) throws IOException {
    enumerateQueries++;
    return namesToIds.lookup(value, () -> {
      enumerateQueriesCacheMisses++;
      return writeValue(value);
    });
  }

  @Override
  public @Nullable String valueOf(final int id) throws IOException {
    valueOfQueries++;
    return idsToNames.lookup(id, () -> {
      valueOfQueriesCacheMisses++;
      final int offset = id;
      return readValueByOffset(offset, /*lengthRef: */null);
    });
  }

  @Override
  public int tryEnumerate(final String name) throws IOException {
    //return NULL_ID if value is not in cache (even if value was enumerated before, but already dropped from cache)
    enumerateQueries++;
    return namesToIds.lookup(name, () -> NULL_ID);
  }

  @Override
  public boolean processAllDataObjects(@NotNull Processor<? super String> processor) throws IOException {
    return forEach((value, id) -> processor.process(value));
  }

  public boolean forEach(final ValuesProcessor<? super String> processor) throws IOException {
    //TODO RC: ProgressManager.checkCanceled() ?
    final long fileSize = file.getLogicalSize();
    int nextOffset = HEADER_SIZE;
    final int[] lengthAndRedirectIdHolder = {0, 0};
    while (nextOffset < fileSize) {
      final int id = nextOffset;
      //TODO RC: check idsToNames.lookup(id, () -> null), and avoid creation of new String if value is already cached
      //         (but still need to read header to calculate fullRecordLength)
      final String value = readValueByOffset(nextOffset, lengthAndRedirectIdHolder);
      final int fullRecordLength = lengthAndRedirectIdHolder[0];
      final int redirectToId = lengthAndRedirectIdHolder[1];
      //report 'original' id, not redirectToId:
      if (!processor.accept(value, id)) {
        return false;
      }
      nextOffset += fullRecordLength;
    }
    return true;
  }

  @Override
  public boolean isDirty() {
    return file.isDirty();
  }

  @Override
  public void force() throws IOException {
    final Lock readLock = file.getStorageLockContext().readLock();
    readLock.lock();
    try {
      file.force();
    }
    finally {
      readLock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    final Lock readLock = file.getStorageLockContext().readLock();
    readLock.lock();
    try {
      file.close();
      final BatchCallback callback = monitoringCallback;
      if (callback != null) {
        callback.close();
      }
    }
    finally {
      readLock.unlock();
    }
  }

  public AutoCloseable enableMonitoring(final @NotNull Meter meter) {
    final var valueOfQueriesCounter = meter.counterBuilder("NamesEnumerator.valueOfQueries").buildObserver();
    final var valueOfQueriesCacheMissesCounter = meter.counterBuilder("NamesEnumerator.valueOfCacheMisses").buildObserver();
    final var enumerateQueriesCounter = meter.counterBuilder("NamesEnumerator.enumerateQueries").buildObserver();
    final var enumerateQueriesCacheMissesCounter = meter.counterBuilder("NamesEnumerator.enumerateCacheMisses").buildObserver();

    monitoringCallback = meter.batchCallback(
      () -> {
        valueOfQueriesCounter.record(valueOfQueries);
        valueOfQueriesCacheMissesCounter.record(valueOfQueriesCacheMisses);
        enumerateQueriesCounter.record(enumerateQueries);
        enumerateQueriesCacheMissesCounter.record(enumerateQueriesCacheMisses);
      },
      valueOfQueriesCounter, valueOfQueriesCacheMissesCounter,
      enumerateQueriesCounter, enumerateQueriesCacheMissesCounter
    );
    return monitoringCallback;
  }


  /**
   * @return offset (which serves also as an id) of the record just written
   */
  private int writeValue(@NotNull String value) throws IOException {
    final byte[] stringBytes = value.getBytes(UTF_8);
    final int stringLength = stringBytes.length;
    final int fullRecordLength = stringLength + RECORD_HEADER_SIZE;
    file.lockWrite();
    try {
      final int offset = nextOffsetToUse;
      file.putInt(offset + RECORD_STRING_LENGTH_OFFSET, stringLength);
      file.putInt(offset + RECORD_REDIRECT_ID_OFFSET, NULL_ID);
      file.put(offset + RECORD_HEADER_SIZE, stringBytes, 0, stringLength);
      nextOffsetToUse += fullRecordLength;
      return offset;
    }
    finally {
      file.unlockWrite();
    }
  }

  /**
   * @param auxRecordData if non-null length=2 array -> auxRecordData[0] contains _total size_ of value record just read,
   *                      auxRecordData[1] contains redirectToId of record just read. If null -> just ignored.
   */
  @NotNull
  private String readValueByOffset(final int offset,
                                   final /*out*/ int[] auxRecordData) throws IOException {
    if (offset + RECORD_HEADER_SIZE > file.getLogicalSize()) {
      throw new IOException("record[" + offset + "]: range[" + offset + ", +" + RECORD_HEADER_SIZE + "b) " +
                            "is outside of file[0," + file.getLogicalSize() + ") " +
                            "-> incorrect record id or file[" + file + "] is corrupted");
    }
    final Lock readLock = file.getStorageLockContext().readLock();
    readLock.lock();
    try {
      final int stringLength = file.getInt(offset + RECORD_STRING_LENGTH_OFFSET);
      final int redirectToId = file.getInt(offset + RECORD_REDIRECT_ID_OFFSET);

      final int fullRecordLength = RECORD_HEADER_SIZE + stringLength;
      if (offset + fullRecordLength > file.getLogicalSize()) {
        throw new IOException("record[" + offset + "]: range[" + offset + ", +" + fullRecordLength + "b) " +
                              "is outside of file[0," + file.getLogicalSize() + ") " +
                              "-> incorrect record id or file[" + file + "] is corrupted");
      }

      final byte[] stringBytes = new byte[stringLength];
      file.get(offset + RECORD_HEADER_SIZE, stringBytes, 0, stringLength, false);

      if (auxRecordData != null) {
        if (auxRecordData.length > 0) {
          auxRecordData[0] = fullRecordLength;
        }
        if (auxRecordData.length > 1) {
          auxRecordData[1] = redirectToId;
        }
      }
      return new String(stringBytes, UTF_8);
    }
    finally {
      readLock.unlock();
    }
  }

  /**
   * Uses fixed-size open-addressing linear-probing hashmap for storing associations. If key is not
   * found, its value is computed, and (key,value) inserted into a map into a free slot (if remains),
   * or, if all slots are occupied, random slot is chosen among probing slots, and replaced. This
   * gives probabilistically kind-of-MRU (old values are likely die out, fresh values are likely
   * cached), but without usage counters/timestamps.
   */
  private static class RandomReplacementStringToIntCache {

    public static final int SIZE = CACHE_SIZE;
    public static final int MAX_PROBES = OffsetBasedNonStrictStringsEnumerator.MAX_PROBES;

    private final String[] keys = new String[SIZE];
    private final int[] values = new int[SIZE];

    public int lookup(final @NotNull String key,
                      final IntThrowableComputable<IOException> orCompute) throws IOException {
      final int hash = key.hashCode();
      final int startIndex = Math.abs(hash) % SIZE;
      for (int probe = 0; probe < MAX_PROBES; probe++) {
        final int index = (startIndex + probe) % SIZE;
        final String candidateKey = keys[index];
        if (candidateKey == null) {//free space still available -> just utilize it for new value
          final int newValue = orCompute.compute();
          if (newValue == NULL_ID) {
            return NULL_ID;
          }
          keys[index] = key;
          values[index] = newValue;
          return newValue;
        }
        else if (key.equals(candidateKey)) {
          return values[index];
        }
      }

      final int newValue = orCompute.compute();
      if (newValue == NULL_ID) {
        return NULL_ID;
      }
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
  private static class RandomReplacementIntToStringCache {
    public static final int SIZE = CACHE_SIZE;
    public static final int MAX_PROBE = MAX_PROBES;

    private final int[] keys = new int[SIZE];
    private final String[] values = new String[SIZE];

    {
      Arrays.fill(keys, NULL_ID);
    }

    public String lookup(final int key,
                         final ThrowableComputable<String, IOException> orCompute) throws IOException {
      final int hash = Integer.hashCode(key);
      final int startIndex = Math.abs(hash) % SIZE;
      for (int probe = 0; probe < MAX_PROBE; probe++) {
        final int index = (startIndex + probe) % SIZE;
        final int candidateKey = keys[index];
        if (candidateKey == NULL_ID) {//free space still available -> just utilize it for new value
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
      if (newValue == null) {
        return null;
      }
      //no more free space remains, need to expurge some record -> choose the random one
      final int probe = ThreadLocalRandom.current().nextInt(MAX_PROBE);
      final int index = (startIndex + probe) % SIZE;
      keys[index] = key;
      values[index] = newValue;
      return newValue;
    }
  }

  private interface IntThrowableComputable<E extends Exception> {
    int compute() throws E;
  }

  @FunctionalInterface
  public interface ValuesProcessor<T> {
    /**
     * @return true if processing to be continued, false if processing should be stopped
     */
    boolean accept(final T value,
                   final int id);
  }


  public static void main(String[] args) throws IOException {
    final File filenamesTxt = new File("./names.txt");

    final File namesFile = new File("./names.names");
    removeRemnantsFromPreviousRuns(namesFile);

    //RC: approximately 2-3 times faster, but approximately 2.2 times larger file

    final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      namesFile.toPath(),
      10 * IOUtil.MiB,
      null,
      IOUtil.MiB,
      false
    );
    final OffsetBasedNonStrictStringsEnumerator enumerator = new OffsetBasedNonStrictStringsEnumerator(mappedFile);
    //final PersistentStringEnumerator enumerator = new PersistentStringEnumerator(namesFile.toPath(), 1024);

    final List<String> paths = Files.readAllLines(filenamesTxt.toPath());
    final long startedAtNs = System.nanoTime();
    long pureLength = 0;
    for (String path : paths) {
      final String name = new File(path).getName();
      enumerator.enumerate(name);

      pureLength += name.length();
    }
    System.out.println(
      TimeoutUtil.getDurationMillis(startedAtNs) + " ms for .enumerate()-ing " + paths.size() + " names (" + pureLength + " chars)");

    final int[] counter = {0};
    enumerator.processAllDataObjects((value) -> {
      counter[0]++;
      return true;
    });
    enumerator.close();

    System.out.println("Names stored: " + counter[0] + ", out of " + paths.size() +
                       " (" + Math.round(counter[0] * 100.0 / paths.size()) + " %)");
    System.out.println("Stored file(s) size: " + totalSize(namesFile) + " bytes");
  }

  private static void removeRemnantsFromPreviousRuns(final File namesBaseFile) {
    final File[] files = new File(".")
      .listFiles((dir, name) -> name.startsWith(namesBaseFile.getName()));
    for (File file : files) {
      file.delete();
    }
    namesBaseFile.delete();
  }

  private static long totalSize(final File namesBaseFile) {
    final File[] files = new File(".")
      .listFiles((dir, name) -> name.startsWith(namesBaseFile.getName()));
    long total = 0;
    for (File file : files) {
      total += file.length();
    }
    return total;
  }
}
