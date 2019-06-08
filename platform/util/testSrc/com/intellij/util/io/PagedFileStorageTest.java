// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.google.common.base.Charsets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ThrowableRunnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.*;

public class PagedFileStorageTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final PagedFileStorage.StorageLock lock = new PagedFileStorage.StorageLock();
  private File f;
  private PagedFileStorage s;

  @Before
  public void setUp() throws IOException {
    withLock(lock, () -> {
      f = tempDir.newFile("storage");
      s = new PagedFileStorage(f, lock);
    });
  }

  @After
  public void tearDown() throws IOException {
    withLock(lock, () -> {
      s.close();
      File l = new File(f.getPath() + ".len");
      assertTrue(l.getPath(), !l.exists() || l.delete());
      assertTrue(f.getPath(), f.delete());
    });
  }

  @Test
  public void testResizing() throws IOException {
    withLock(lock, () -> {
      assertEquals(0, f.length());

      s.resize(12345);
      assertEquals(12345, f.length());

      s.resize(123);
      assertEquals(123, f.length());
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
      ResizeableMappedFile file = new ResizeableMappedFile(f, 2000000, lock);

      System.out.println("writing...");
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
      System.out.println("done in " + t + " ms");

      file.putInt(Integer.MAX_VALUE + 20L, 5678);
      assertEquals(5678, file.getInt(Integer.MAX_VALUE + 20L));

      t = System.currentTimeMillis();
      System.out.println("checking...");
      for (int index = 0, pct = 0; index <= 2000000000; index += 2000000, pct++) {
        assertEquals(index, file.getInt(index));
        printPct(pct);
      }
      assertEquals(1234, file.getInt(Integer.MAX_VALUE - 20));
      t = System.currentTimeMillis() - t;
      System.out.println("done in " + t + " ms");

      file.close();
    });
  }

  @Test
  public void testResizeableMappedFile2() throws IOException {
    withLock(lock, () -> {
      int initialSize = 4096;
      ResizeableMappedFile file = new ResizeableMappedFile(f, initialSize, lock.myDefaultStorageLockContext, PagedFileStorage.MB, false);
      byte[] bytes = StringUtil.repeat("1", initialSize + 2).getBytes(Charsets.UTF_8);
      assertTrue(bytes.length > initialSize);

      file.put(0, bytes, 0, bytes.length);
      int written_bytes = (int)file.length();
      byte[] newBytes = new byte[written_bytes];
      file.get(0, newBytes, 0, written_bytes);
      assertArrayEquals(bytes, newBytes);

      file.close();
    });
  }

  private static void withLock(PagedFileStorage.StorageLock lock, ThrowableRunnable<IOException> block) throws IOException {
    lock.lock();
    try {
      block.run();
    }
    finally {
      lock.unlock();
    }
  }

  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private static void printPct(int pct) {
    if (pct < 1000 && pct % 100 == 0) {
      System.out.println("  [" + FORMATTER.format(new Date()) + "] " + pct / 10 + "%");
    }
  }
}