// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.mmapped;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Storage over memory-mapped file.
 * Hides most of the peculiarities of mmapped-files.
 * But still very low-level, so use with caution -- or better don't. Better use higher-level components, like
 * {@link com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper} or {@link com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes}
 */
@ApiStatus.Internal
public final class MMappedFileStorage implements Closeable, Unmappable, CleanableStorage {
  private static final Logger LOG = Logger.getInstance(MMappedFileStorage.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, 1000);

  /**
   * Use fsync() on .flush() by default? <br/>
   * Under normal conditions, there is no need to issue .fsync() on mapped storages -- OS is responsible
   * for page flushing even if an app crashes.<br/>
   * The only reason for doing .fsync() is to prevent data loss on OS crash/power outage, which is
   * a) quite rare cases
   * b) I doubt our persistent data structures really <i>could</i> provide reliability in such cases anyway
   * -- so the flag is false by default.<br/>
   * The flag seems quite generic, so made public, for all memory-mapped storages to refer.
   */
  public static final boolean FSYNC_ON_FLUSH_BY_DEFAULT = getBooleanProperty("MMappedFileStorage.FSYNC_BY_DEFAULT_ON_FLUSH", false);


  //Explicit 'unmap' vs rely on JVM which will unmap pages eventually, as they are collected by GC?
  //  Explicit unmap allows to 'clean after yourself', but carries a risk of JVM crash if somebody still tries
  //  to access unmapped pages.
  //  Current take:
  //  1) .close() by default rely on GC
  //  2) .close(unmap=true) method closes AND unmap explicitly if needed (synonym: .closeAndUnsafelyUnmap())
  //  3) .closeAndClean() method always unmaps buffers explicitly, since on Windows it is impossible to delete
  //     (=clean) the files that are currently mapped, and GC is proved unreliable for that task.

  /** 'always', 'never', 'on-windows' */
  private static final String UNMAP_ON_CLOSE_KIND = System.getProperty("MMappedFileStorage.UNMAP_ON_CLOSE", "never");
  private static final boolean UNMAP_ON_CLOSE = "always".equals(UNMAP_ON_CLOSE_KIND)
                                                || ("on-windows".equals(UNMAP_ON_CLOSE_KIND) && SystemInfoRt.isWindows);

  /**
   * What if memory mapped buffer is impossible to unmap (by any reason: can't access Unsafe, bad luck, etc)?
   * True: throw an exception
   * False: log warning, and continue (i.e. rely on GC to unmap the buffer eventually)
   */
  private static final boolean FAIL_ON_FAILED_UNMAP = getBooleanProperty("idea.fs.fail-if-unmap-failed", true);

  /** Log each unmapped buffer */
  private static final boolean LOG_UNMAP_OPERATIONS = getBooleanProperty("MMappedFileStorage.LOG_UNMAP_OPERATIONS", false);


  //============== statistics/monitoring: ===================================================================

  //Keep track of mapped buffers allocated & their total size, numbers are reported to OTel.Metrics.
  //Why: mapped buffers are limited resources (~16k on linux by default?), so it is worth to monitor
  //     how we use them, and issue the alarm early on as we start to use too many

  /** Log warn if > PAGES_TO_WARN_THRESHOLD pages were mapped */
  private static final int PAGES_TO_WARN_THRESHOLD = getIntProperty("vfs.memory-mapped-storage.pages-to-warn-threshold", 256);

  private static volatile int openedStoragesCount = 0;
  private static final AtomicInteger totalPagesMapped = new AtomicInteger();
  private static final AtomicLong totalBytesMapped = new AtomicLong();
  /** total time (nanos) spent inside {@link Page#map(FileChannel, int)} call */
  private static final AtomicLong totalTimeForPageMapNs = new AtomicLong();

  /** Track opened storages to prevent open the same file more than once: Map[absolutePath -> storage] */
  //@GuardedBy(openedStorages)
  private static final Map<Path, MMappedFileStorage> openedStorages = new HashMap<>();

  //=========================================================================================================


  private final Path storagePath;

  private final int pageSize;
  private final int pageSizeMask;
  private final int pageSizeBits;

  private final FileChannel channel;

  private final transient Object pagesLock = new Object();
  /** see comments in {@link #pageByIndex(int)} */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Page[] pages;

  public MMappedFileStorage(Path path,
                            int pageSize) throws IOException {
    this(path, pageSize, 0);
  }

  private MMappedFileStorage(Path path,
                             int pageSize,
                             int pagesCountToMapInitially) throws IOException {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    if (pagesCountToMapInitially < 0) {
      throw new IllegalArgumentException("pagesCountToMapInitially(=" + pagesCountToMapInitially + ") must be >= 0");
    }

    pageSizeBits = Integer.numberOfTrailingZeros(pageSize);
    pageSizeMask = pageSize - 1;
    this.pageSize = pageSize;

    Path absolutePath = path.toAbsolutePath();
    this.storagePath = absolutePath;

    synchronized (openedStorages) {
      MMappedFileStorage alreadyExistingStorage = openedStorages.get(absolutePath);
      if (alreadyExistingStorage != null) {
        throw new IllegalStateException("Storage[" + absolutePath + "] is already opened (and not yet closed)" +
                                        " -- can't open same file more than once");
      }

      channel = FileChannel.open(storagePath, READ, WRITE, CREATE);

      //map initial pages:
      pages = new Page[pagesCountToMapInitially];
      for (int i = 0; i < pagesCountToMapInitially; i++) {
        pageByIndex(i);
      }

      openedStorages.put(absolutePath, this);
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      openedStoragesCount++;
    }
  }

  public Path storagePath() {
    return storagePath;
  }

  public int pageSize() {
    return pageSize;
  }

  public ByteOrder byteOrder() {
    return nativeOrder();
  }

  public boolean isOpen() {
    return channel.isOpen();
  }

  /**
   * @return current file size. Returned size is always N*pageSize
   * </p>
   * <b>BEWARE</b>: file pages are mapped <i>lazily</i>, hence if file size = N*pageSize -- it does NOT mean there
   * are N {@link Page}s currently mapped into memory -- it could be only pages #0, 5, 8, N-1 are mapped, but
   * pages in between those were never requested, hence not mapped (yet?).
   */
  public long actualFileSize() throws IOException {
    synchronized (pagesLock) {
      //RC: Lock the pages to prevent file expansion (file expansion also acquires .pagesLock, see .pageByOffset())
      //    If file is expanded concurrently with this method, we could see not-pageSize-aligned file size,
      //    which is confusing to deal with.
      //    Better to just prohibit such cases: file expansion (=new page allocation) is a relatively rare
      //    event, so this lock is mostly uncontended, so the cost is negligible.
      long channelSize = channel.size();
      if ((channelSize & pageSizeMask) != 0) {
        throw new AssertionError("Bug: [" + storagePath + "].channelSize(=" + channelSize + ") is not pageSize(=" + pageSize + ")-aligned");
      }
      return channelSize;
    }
  }

  public @NotNull Page pageByOffset(long offsetInFile) throws IOException {
    int pageIndex = pageIndexByOffset(offsetInFile);
    return pageByIndex(pageIndex);
  }

  public @NotNull Page pageByIndex(int pageIndex) throws IOException {
    //We access .pages through data-race. This is a benign race, though: basically, the only values one could
    // read from .pages[i] is {null, Page(i)} -- because those are the only values that are written to .pages[i]
    // across all the codebase, and JMM guarantees no 'out-of-thin-air' values even in the presence of data race.
    //Now Page class is immutable (all fields final), hence it could be 'safe-published' even through data race,
    // so if we read non-null value from .pages[pageIndex] value -- it is a correctly initialized Page(pageIndex)
    // value that is OK to use. If we read null -- we dive into .pageByIndexLocked() there everything (.pages
    // resizing and new page mapping) happens under good-old exclusive lock, that guarantees visibility of all
    // changes done by other threads, and absence of Page duplicates.
    //
    Page page = pageOrNull(pages, pageIndex);
    if (page == null) {
      page = pageByIndexLocked(pageIndex);
    }
    return page;
  }

  /**
   * Truncates the file so that it has size=0, and all previous content is lost.
   * This method is unsafe and should be used with caution: it should be no chance storage is used by other
   * threads concurrently, nobody should keep any {@link Page} reference. This is because writing to a buffer
   * mapped over a non-existing file region (e.g. after truncation) is 'undefined behavior', and could lead
   * to all sorts of weird behaviors -- immediate/delayed JVM crash (#SIGBUS), immediate/delayed data loss, etc.
   * <p/>
   * Basically, the main safe use-case for this method is to call it immediately after the storage instance
   * is opened -- and no reference to it is ever leaked. E.g., one opens the file, reads the header, and
   * finds out file content is corrupted -- so .truncate() the storage, and use as-if it was a new file
   * just created.
   *
   * @deprecated to be removed: it doesn't work on Windows, but Windows was the main reason to introduce
   * the method in the first place, so better get rid of it before it got more usages.
   */
  @ApiStatus.Obsolete
  public void truncate() throws IOException {
    synchronized (pagesLock) {
      channel.truncate(0L);
      pages = new Page[0];
    }
  }

  public int pageIndexByOffset(long offsetInFile) {
    if (offsetInFile < 0) {
      throw new IllegalArgumentException("offsetInFile(=" + offsetInFile + ") must be >=0");
    }
    return (int)(offsetInFile >> pageSizeBits);
  }

  public int toOffsetInPage(long offsetInFile) {
    return (int)(offsetInFile & pageSizeMask);
  }

  @Override
  public void close() throws IOException {
    close( /*unmap: */ UNMAP_ON_CLOSE);
  }

  /**
   * Close the storage, and unmap all the pages mapped.
   * BEWARE: explicit buffer unmapping is unsafe, since any use of mapped buffer after unmapping leads to a
   * JVM crash. Because of that, this method is inherently risky.
   * 'Safe' use of this method requires all the uses of this storage to be stopped beforehand -- ideally, it
   * should be no reference to any Page alive/in use by any thread, i.e., no chance any thread accesses any Page
   * of this storage after _starting_ the invocation of this method.
   * Generally, it is much safer to call {@link #close()} without unmap -- in which case JVM/GC is responsible to
   * keep buffers mapped until at least someone uses them.
   */
  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    close( /*unmap: */ true);
  }

  public void fsync() throws IOException {
    if (channel.isOpen()) {
      channel.force(true);
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    //on Windows it is impossible to delete the file without unmapping it first, so take the risk:
    closeAndUnsafelyUnmap();
    FileUtil.delete(storagePath);
  }

  /**
   * Fills with zeroes a region {@code [startOffsetInFile..endOffsetInFile]} (both ends inclusive)
   * </p>
   * <b>BEWARE</b>: Method checks offsets for negativity, and throws {@link IllegalArgumentException}, but doesn't
   * check anything else, just does what was asked: i.e.
   * 1) if (end < start)         => method zeroize nothing
   * 2) if (end   > end-of-file) => method zeroize until the end requested, _expanding_ file along the way
   */
  public void zeroizeRegion(long startOffsetInFile,
                            long endOffsetInFile) throws IOException {
    if (startOffsetInFile < 0) {
      throw new IllegalArgumentException("startOffsetInFile(=" + startOffsetInFile + ") must be >=0");
    }
    if (endOffsetInFile < 0) {
      throw new IllegalArgumentException("endOffsetInFile(=" + endOffsetInFile + ") must be >=0");
    }
    for (long offset = startOffsetInFile; offset <= endOffsetInFile; ) {
      Page page = pageByOffset(offset);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      int startOffsetInPage = toOffsetInPage(offset);
      int endOffsetInPage = endOffsetInFile > page.lastOffsetInFile() ?
                            pageSize - 1 : toOffsetInPage(endOffsetInFile);
      //MAYBE RC: it could be done much faster -- with putLong(), with preallocated array of zeroes,
      //          with Unsafe.setMemory() -- but does it worth it?
      for (int pos = startOffsetInPage; pos <= endOffsetInPage; pos++) {
        //TODO RC: make putLong() (but check both startOffsetInPage and endOffsetInPage are 64-aligned)
        pageBuffer.put(pos, (byte)0);
      }

      offset += (endOffsetInPage - startOffsetInPage) + 1;
    }
  }

  /**
   * Fills with zeroes a region starting with startOffsetInFile (inclusive) and until the end-of-file.
   * If startOffsetInFile is beyond EOF -- do nothing
   */
  public void zeroizeTillEOF(long startOffsetInFile) throws IOException {
    long actualFileSize = actualFileSize();
    if (actualFileSize == 0) {
      return;
    }
    zeroizeRegion(startOffsetInFile, actualFileSize - 1);
  }

  @Override
  public String toString() {
    return "MMappedFileStorage[" + storagePath + "]" +
           "[" + pages.length + " pages of " + pageSize + "b]";
  }

  private void close(boolean unmap) throws IOException {
    try {
      synchronized (pagesLock) {
        if (channel.isOpen()) {
          channel.close();
          for (Page page : pages) {
            if (page != null) {
              unregisterMappedPage(pageSize);
            }
          }
        }

        if (unmap) {
          try {
            for (Page page : pages) {
              if (page != null) {
                page.unmap();
              }
            }
          }
          finally {
            //Tradeoff: we can _always_ clean the .pages array, regardless of unmap or not -- then .close(unmap:false)
            // benefits from faster page unmapping by GC (it is quite often storage itself is still strongly-reachable
            // after the .close() => without nulling the .pages pageBuffers also remain strongly-reachable => not
            // unmapped)
            // But this way we lose the ability to ask storage explicitly unmap pages _after_ the .close() -- because
            // .pages is already cleared, nothing to unmap.
            // This close-then-unmap is really frequent scenario: it is quite often storage is .close()-ed during
            // some regular resource-deallocation-procedure, but in the very end we want to _delete_ the storage
            // file, hence we need to explicitly unmap it beforehand -- which we can't do if page refs were already
            // cleared during regular .close()
            // So I chose to lean the other side, and do NOT clear page refs on close(unmap:false), so later
            // .close(unmap:true) if called -- could still unmap them. But this means that without explicit
            // .close(unmap:true) mapped buffers will remain mapped for longer, even after storage was already
            // .close()-ed.
            Arrays.fill(pages, null);
          }
        }
      }
    }
    finally {
      synchronized (openedStorages) {
        MMappedFileStorage removed = openedStorages.get(storagePath);
        if (removed == this) {
          openedStorages.remove(storagePath);
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          openedStoragesCount--;
        }
      }
    }
  }

  private Page pageByIndexLocked(int pageIndex) throws IOException {
    synchronized (pagesLock) {
      if (!channel.isOpen()) {
        throw new ClosedStorageException("Storage already closed");
      }
      if (pageIndex >= pages.length) {
        pages = Arrays.copyOf(pages, pageIndex + 1);
      }
      Page page = pages[pageIndex];

      if (page == null) {
        page = new Page(pageIndex, channel, pageSize, byteOrder());
        pages[pageIndex] = page;

        registerMappedPage(pageSize);
      }
      return page;
    }
  }

  private static @Nullable Page pageOrNull(Page[] pages,
                                           int pageIndex) {
    if (0 <= pageIndex && pageIndex < pages.length) {
      return pages[pageIndex];
    }
    return null;
  }

  public static final class Page {
    private final int pageIndex;
    private final int pageSize;
    private final long offsetInFile;
    private final ByteBuffer pageBuffer;

    private Page(int pageIndex,
                 @NotNull FileChannel channel,
                 int pageSize,
                 @NotNull ByteOrder byteOrder) throws IOException {
      this.pageIndex = pageIndex;
      this.pageSize = pageSize;
      this.offsetInFile = pageIndex * (long)pageSize;
      this.pageBuffer = map(channel, pageSize).order(byteOrder);
    }

    private MappedByteBuffer map(FileChannel channel,
                                 int pageSize) throws IOException {
      //MAYBE RC: this could cause noticeable pauses, hence it may worth to enlarge file in advance, async?
      //          i.e. schedule enlargement as soon as last page is 50% full? It wouldn't work good for
      //          completely random-access storages, but most our use-cases are either append-only logs,
      //          or file-attributes, which indexed by fileId, which are growing quite monotonically,
      //          so it may work
      long startedAtNs = System.nanoTime();
      try {
        IOUtil.allocateFileRegion(channel, offsetInFile + pageSize);
        return channel.map(READ_WRITE, offsetInFile, pageSize);
      }
      finally {
        long timeSpentNs = System.nanoTime() - startedAtNs;
        totalTimeForPageMapNs.addAndGet(timeSpentNs);
      }
    }

    private void unmap() throws IOException {
      try {
        unmapBuffer(pageBuffer);
      }
      catch (Throwable t) {
        if (FAIL_ON_FAILED_UNMAP) {
          throw new IOException("Can't unmap pageBuffer", t);
        }
        else {
          THROTTLED_LOG.warn("Can't unmap pageBuffer explicitly -- rely on GC to do it eventually", t);
        }
      }
    }

    public ByteBuffer rawPageBuffer() {
      return pageBuffer;
    }

    public long firstOffsetInFile() {
      return offsetInFile;
    }

    public long lastOffsetInFile() {
      return offsetInFile + pageSize - 1;
    }

    @Override
    public String toString() {
      return "Page[#" + pageIndex + "][offset: " + offsetInFile + ", length: " + pageBuffer.capacity() + " b)";
    }


    public static void unmapBuffer(@NotNull ByteBuffer buffer) throws Exception {
      if (!buffer.isDirect()) {
        return;
      }
      if (INVOKE_CLEANER_METHOD == null) {
        throw new IllegalStateException("No access to Unsafe.invokeCleaner() -- explicit mapped buffers unmapping is unavailable");
      }

      INVOKE_CLEANER_METHOD.invoke(ReflectionUtil.getUnsafe(), buffer);
      if (LOG_UNMAP_OPERATIONS) {
        LOG.info("Buffer unmapped: " + buffer);
      }
    }

    private static final Method INVOKE_CLEANER_METHOD;

    static {
      Method cleanerMethod;
      try {
        Object unsafe = ReflectionUtil.getUnsafe();
        Class<?> unsafeClass = unsafe.getClass();
        cleanerMethod = ReflectionUtil.getDeclaredMethod(unsafeClass, "invokeCleaner", ByteBuffer.class);
      }
      catch (Throwable t) {
        LOG.error("Can't get access to Unsafe.invokeCleaner() -- explicit mapped buffers unmapping will be unavailable", t);
        cleanerMethod = null;
      }
      INVOKE_CLEANER_METHOD = cleanerMethod;
    }
  }

  // ============ statistics accessors ======================================================================

  public static int openedStoragesCount() {
    return openedStoragesCount;
  }

  public static int totalPagesMapped() {
    return totalPagesMapped.get();
  }

  public static long totalBytesMapped() {
    return totalBytesMapped.get();
  }

  /** total time spent inside {@link Page#map(FileChannel, int)} call (including file expansion/zeroing, if needed) */
  public static long totalTimeForPageMap(@NotNull TimeUnit unit) {
    return unit.convert(totalTimeForPageMapNs.get(), NANOSECONDS);
  }

  // ============ statistics infra  ========================================================================

  private static void registerMappedPage(int pageSize) {
    int pagesMapped = totalPagesMapped.incrementAndGet();
    totalBytesMapped.addAndGet(pageSize);

    if (pagesMapped > PAGES_TO_WARN_THRESHOLD) {
      THROTTLED_LOG.warn("Too many pages were mapped: " + pagesMapped + " > " + PAGES_TO_WARN_THRESHOLD + " threshold. " +
                         "Total mapped size: " + totalBytesMapped.get() + " bytes, storages: " + openedStoragesCount);
    }
  }

  private static void unregisterMappedPage(int pageSize) {
    totalPagesMapped.decrementAndGet();
    totalBytesMapped.addAndGet(-pageSize);
  }
}
