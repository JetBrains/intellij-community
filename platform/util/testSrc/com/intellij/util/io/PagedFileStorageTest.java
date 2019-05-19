/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;

public class PagedFileStorageTest extends TestCase {
  private final PagedFileStorage.StorageLock lock = new PagedFileStorage.StorageLock();
  private File f;
  private PagedFileStorage s;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    lock.lock();
    try {
      f = FileUtil.createTempFile("storage", ".tmp");
      s = new PagedFileStorage(f, lock);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public void tearDown() throws Exception {
    lock.lock();
    try {
      s.close();
      final File l = new File(f.getPath() + ".len");
      assert !l.exists() || l.delete() : l.getPath();
      assert f.delete() : f.getPath();
    } finally {
      lock.unlock();
      super.tearDown();
    }
  }

  public void testResizing() throws IOException {
    lock.lock();
    try {
      assertEquals(0, f.length());

      s.resize(12345);
      assertEquals(12345, f.length());

      s.resize(123);
      assertEquals(123, f.length());
    } finally {
      lock.unlock();
    }
  }

  public void testFillingWithZerosAfterResize() throws IOException {
    lock.lock();
    try {
      s.resize(1000);

      for (int i = 0; i < 1000; i++) {
        assertEquals(0, s.get(i));
      }
    } finally {
      lock.unlock();
    }
  }

  public void testResizeableMappedFile() throws Exception {
    lock.lock();
    try {
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
    } finally {
      lock.unlock();
    }
  }

  public void testResizeableMappedFile2() throws Exception {
    lock.lock();
    try {
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
    } finally {
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
