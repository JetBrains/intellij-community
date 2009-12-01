/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class PagedFileStorageTest extends TestCase {
  private File f;
  private PagedFileStorage s;
  private PagedFileStorage.StorageLock lock;

  public void setUp() throws Exception {
    f = File.createTempFile("storage", ".tmp");
    lock = new PagedFileStorage.StorageLock();
    s = new PagedFileStorage(f, lock);
  }

  public void tearDown() {
    f.delete();
  }

  public void testResizing() throws IOException {
    synchronized (lock) {
      assertEquals(0, f.length());

      s.resize(12345);
      assertEquals(12345, f.length());

      s.resize(123);
      assertEquals(123, f.length());
    }
  }

  public void testFillingWithZerosAfterResize() throws IOException {
    synchronized (lock) {
      s.resize(1000);

      for (int i = 0; i < 1000; i++) {
        assertEquals(0, s.get(i));
      }
    }
  }
}
