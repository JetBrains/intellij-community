// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ThrowableRunnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.*;

public class PagedFileStorageTest {
  private static final Logger LOG = Logger.getInstance(PagedFileStorageTest.class);
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final StorageLockContext lock = new StorageLockContext(true);
  private Path f;
  private PagedFileStorage s;

  @Before
  public void setUp() throws IOException {
    withLock(lock, () -> {
      f = tempDir.newFile("storage").toPath();
      s = new PagedFileStorage(f, lock, PagedFileStorage.BUFFER_SIZE, false, false);
    });
  }

  @After
  public void tearDown() throws IOException {
    withLock(lock, () -> {
      s.close();
      Path l = f.resolveSibling(f.getFileName() + ".len");
      assertTrue(l.toString(), !Files.exists(l) || Files.deleteIfExists(l));
      assertTrue(f.toString(), Files.deleteIfExists(f));
    });
  }

  @Test
  public void testResizing() throws IOException {
    withLock(lock, () -> {
      assertEquals(0, Files.size(f));

      s.resize(12345);
      assertEquals(12345, Files.size(f));

      s.resize(123);
      assertEquals(123, Files.size(f));
    });
  }

  @Test
  public void testFillingWithZerosAfterResize() throws IOException {
    withLock(lock, () -> {
      s.resize(1000);

      for (int i = 0; i < 1000; i++) {
        assertEquals(0, s.get(i));
      }
    });
  }

  @Test
  public void testResizeableMappedFile() throws IOException {
    withLock(lock, () -> {
      ResizeableMappedFile file = new ResizeableMappedFile(f, 2000000, lock, -1, false);

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

      file.close();
    });
  }

  @Test
  public void testResizeableMappedFile2() throws IOException {
    withLock(lock, () -> {
      int initialSize = 4096;
      ResizeableMappedFile file = new ResizeableMappedFile(f, initialSize, lock, PagedFileStorage.MB, false);
      byte[] bytes = StringUtil.repeat("1", initialSize + 2).getBytes(StandardCharsets.UTF_8);
      assertTrue(bytes.length > initialSize);

      file.put(0, bytes, 0, bytes.length);
      int written_bytes = (int)file.length();
      byte[] newBytes = new byte[written_bytes];
      file.get(0, newBytes, 0, written_bytes);
      assertArrayEquals(bytes, newBytes);

      file.close();
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

  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private static void printPct(int pct) {
    if (pct < 1000 && pct % 100 == 0) {
      LOG.debug("  [" + FORMATTER.format(new Date()) + "] " + pct / 10 + "%");
    }
  }
}