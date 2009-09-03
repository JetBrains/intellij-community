package com.intellij.util.io;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class PagedFileStorageTest extends TestCase {
  private File f;
  private PagedFileStorage s;

  public void setUp() throws Exception {
    f = File.createTempFile("storage", ".tmp");
    s = new PagedFileStorage(f);
  }

  public void tearDown() {
    f.delete();
  }

  public void testResizing() throws IOException {
    assertEquals(0, f.length());

    s.resize(12345);
    assertEquals(12345, f.length());

    s.resize(123);
    assertEquals(123, f.length());
  }

  public void testFillingWithZerosAfterResize() throws IOException {
    s.resize(1000);

    for (int i = 0; i < 1000; i++) {
      assertEquals(0, s.get(i));
    }
  }
}