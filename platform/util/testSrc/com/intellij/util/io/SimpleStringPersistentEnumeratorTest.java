// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;

public class SimpleStringPersistentEnumeratorTest {
  @Rule
  public TempDirectory myTempDirectory = new TempDirectory();

  @Test
  public void testNull() {
    Path enumPath = myTempDirectory.newDirectoryPath().resolve("test.enum");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    int id = enumerator.enumerate(null);
    Assert.assertEquals(1, id);
  }

  @Test
  public void testMultiline() {
    Path enumPath = myTempDirectory.newDirectoryPath().resolve("test.enum");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    try {
      enumerator.enumerate("qwe\nasd");
      Assert.fail();
    }
    catch (RuntimeException e) {
      Assert.assertEquals("SimpleStringPersistentEnumerator doesn't support multi-line strings", e.getMessage());
    }
  }

  @Test
  public void testSeveralEnumeratedIds() {
    Path enumPath = myTempDirectory.newDirectoryPath().resolve("test.enum");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    int id1 = enumerator.enumerate("qwe");
    int id2 = enumerator.enumerate("asd");
    int id3 = enumerator.enumerate("qwe");
    Assert.assertEquals(1, id1);
    Assert.assertEquals(2, id2);
    Assert.assertEquals(1, id3);
  }
}
