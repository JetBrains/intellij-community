// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.stats.CachedChannelsStatistics;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.intellij.util.io.PageCacheUtils.DEFAULT_PAGE_SIZE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class PagedFileStorageTest {
  private static final Logger LOG = Logger.getInstance(PagedFileStorageTest.class);

  @Rule public TempDirectory tempDir = new TempDirectory();

  private final StorageLockContext storageLockContext = new StorageLockContext();
  private Path file;
  private PagedFileStorage pagedFileStorage;

  @Before
  public void setUp() throws IOException {
    file = tempDir.newFileNio("storage");
    withLock(storageLockContext, () -> {
      pagedFileStorage = new PagedFileStorage(file, storageLockContext, DEFAULT_PAGE_SIZE, false, false);
    });
  }

  @After
  public void tearDown() throws IOException {
    withLock(storageLockContext, () -> {
      pagedFileStorage.close();
      Path l = file.resolveSibling(file.getFileName() + ".len");
      assertTrue(l.toString(), !Files.exists(l) || Files.deleteIfExists(l));
      assertTrue(file.toString(), Files.deleteIfExists(file));
    });
  }

  @Test
  public void testResizing() throws IOException {
    withLock(storageLockContext, () -> {
      assertEquals(0, Files.size(file));

      pagedFileStorage.resize(12345);
      assertEquals(12345, Files.size(file));

      pagedFileStorage.resize(123);
      assertEquals(123, Files.size(file));
    });
  }

  @Test
  public void testFillingWithZerosAfterResize() throws IOException {
    withLock(storageLockContext, () -> {
      pagedFileStorage.resize(1000);

      for (int i = 0; i < 1000; i++) {
        assertEquals(0, pagedFileStorage.get(i, true));
      }
    });
  }

  @Test
  public void testForceWritesOnlyActuallyModifiedRegion() throws IOException {
    withLock(storageLockContext, () -> {
      byte[] initialBytes = new byte[128];
      Arrays.fill(initialBytes, (byte)1);
      pagedFileStorage.put(0, initialBytes, 0, initialBytes.length);
      pagedFileStorage.force();

      byte[] externallyChangedBytes = initialBytes.clone();
      externallyChangedBytes[0] = 42;
      Files.write(file, externallyChangedBytes);

      int modifiedOffset = 64;
      pagedFileStorage.put(modifiedOffset, (byte)7);
      pagedFileStorage.force();

      byte[] actualBytes = Files.readAllBytes(file);
      assertEquals(42, actualBytes[0]);
      assertEquals(7, actualBytes[modifiedOffset]);
    });
  }

  @Test
  public void testWritableStorageUsesWritableChannelsForAllOperations() throws IOException {
    Path path = tempDir.newFileNio("writable-storage-channel-mode");
    RecordingChannelsAccessor channelsAccessor = new RecordingChannelsAccessor();
    StorageLockContext lockContext = new StorageLockContext(false, channelsAccessor);

    withLock(lockContext, () -> {
      try (PagedFileStorage storage = new PagedFileStorage(path, lockContext, DEFAULT_PAGE_SIZE, false, false)) {
        storage.resize(16);
        storage.put(0, (byte)42);
        storage.force();

        assertEquals(42, storage.get(0, true));
        assertEquals(16, storage.length());
        assertEquals(42, (int)storage.readInputStream(input -> input.read()));
        assertEquals(42, (int)storage.readChannel(channel -> {
          ByteBuffer byteBuffer = ByteBuffer.allocate(1);
          assertEquals(1, channel.read(byteBuffer));
          return (int)byteBuffer.get(0);
        }));
      }
    });

    channelsAccessor.assertOnlyReadOnlyMode(false);
  }

  @Test
  public void testReadOnlyStorageUsesReadOnlyChannelsForAllOperations() throws IOException {
    Path path = tempDir.newFileNio("readonly-storage-channel-mode");
    Files.write(path, new byte[]{42});

    RecordingChannelsAccessor channelsAccessor = new RecordingChannelsAccessor();
    StorageLockContext lockContext = new StorageLockContext(false, channelsAccessor);

    PersistentHashMapValueStorage.CreationTimeOptions.threadLocalOptions().readOnly(true).with(() -> {
      withLock(lockContext, () -> {
        try (PagedFileStorage storage = new PagedFileStorage(path, lockContext, DEFAULT_PAGE_SIZE, false, false)) {
          assertEquals(42, storage.get(0, true));
          assertEquals(1, storage.length());
          assertEquals(42, (int)storage.readInputStream(input -> input.read()));
          assertEquals(42, (int)storage.readChannel(channel -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1);
            assertEquals(1, channel.read(byteBuffer));
            return (int)byteBuffer.get(0);
          }));
        }
      });
      return null;
    });

    channelsAccessor.assertOnlyReadOnlyMode(true);
  }

  @Test
  public void testResizeableMappedFile() throws IOException {
    Path newPath = tempDir.newFile("storage-1").toPath();
    long freeSpace = Files.getFileStore(newPath).getUsableSpace();
    assumeTrue("test requires at least 2Gb of empty disk space", 2L * IOUtil.GiB < freeSpace);
    withLock(storageLockContext, () -> {
      try (ResizeableMappedFile file = new ResizeableMappedFile(newPath, 2000000, storageLockContext, -1, false)) {
        LOG.debug("writing...");
        long t = System.currentTimeMillis();
        for (int index = 0, pct = 0; index <= 2000000000; index += 2000000, pct++) {
          file.putInt(index, index);
          assertTrue(file.length() > index);
          assertEquals(index, file.getInt(index));
          printPct(pct);
        }
        file.putInt(Integer.MAX_VALUE - 20, 1234);
        assertEquals(1234, file.getInt(Integer.MAX_VALUE - 20));
        t = System.currentTimeMillis() - t;
        LOG.debug("done in " + t + " ms");

        file.putInt(Integer.MAX_VALUE + 20L, 5678);
        assertEquals(5678, file.getInt(Integer.MAX_VALUE + 20L));

        t = System.currentTimeMillis();
        LOG.debug("checking...");
        for (int index = 0, pct = 0; index <= 2000000000; index += 2000000, pct++) {
          assertEquals(index, file.getInt(index));
          printPct(pct);
        }
        assertEquals(1234, file.getInt(Integer.MAX_VALUE - 20));
        t = System.currentTimeMillis() - t;
        LOG.debug("done in " + t + " ms");
      }
    });
  }

  @Test
  public void testResizeableMappedFile2() throws IOException {
    Path newPath = tempDir.newFile("storage-2").toPath();
    withLock(storageLockContext, () -> {
      int initialSize = 4096;
      try (ResizeableMappedFile file = new ResizeableMappedFile(newPath, initialSize, storageLockContext, IOUtil.MiB, false)) {
        byte[] bytes = StringUtil.repeat("1", initialSize + 2).getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length > initialSize);

        file.put(0, bytes, 0, bytes.length);
        int written_bytes = (int)file.length();
        byte[] newBytes = new byte[written_bytes];
        file.get(0, newBytes, 0, written_bytes, true);
        assertArrayEquals(bytes, newBytes);
      }
    });
  }

  private static void withLock(StorageLockContext lock, ThrowableRunnable<IOException> block) throws IOException {
    lock.lockWrite();
    try {
      block.run();
    }
    finally {
      lock.unlockWrite();
    }
  }

  private static final class RecordingChannelsAccessor implements ChannelsAccessor {
    private final ChannelsAccessor delegate = PageCacheUtils.CHANNELS_NO_CACHE;
    private final List<Boolean> readOnlyModes = new ArrayList<>();

    @Override
    public @NotNull CachedChannelsStatistics getStatistics() {
      return delegate.getStatistics();
    }

    @Override
    public <T> T executeOp(@NotNull Path path, @NotNull FileChannelOperation<T> operation, boolean readOnly) throws IOException {
      readOnlyModes.add(readOnly);
      return delegate.executeOp(path, operation, readOnly);
    }

    @Override
    public <T> T executeIdempotentOp(@NotNull Path path,
                                     @NotNull FileChannelInterruptsRetryer.FileChannelIdempotentOperation<T> operation,
                                     boolean readOnly) throws IOException {
      readOnlyModes.add(readOnly);
      return delegate.executeIdempotentOp(path, operation, readOnly);
    }

    @Override
    public void closeChannel(@NotNull Path path) throws IOException {
      delegate.closeChannel(path);
    }

    private void assertOnlyReadOnlyMode(boolean expectedReadOnly) {
      assertFalse(readOnlyModes.toString(), readOnlyModes.isEmpty());
      for (boolean readOnly : readOnlyModes) {
        assertEquals(readOnlyModes.toString(), expectedReadOnly, readOnly);
      }
    }
  }

  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private static void printPct(int pct) {
    if (pct < 1000 && pct % 100 == 0) {
      LOG.debug("  [" + FORMATTER.format(new Date()) + "] " + pct / 10 + "%");
    }
  }
}