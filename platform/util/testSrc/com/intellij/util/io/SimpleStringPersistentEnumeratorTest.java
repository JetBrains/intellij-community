// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

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
      fail("SimpleStringPersistentEnumerator must throw exception on multi-line strings");
    }
    catch (RuntimeException e) {
      assertThat(
        e.getMessage(),
        startsWith("SimpleStringPersistentEnumerator doesn't support multi-line strings")
      );
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

  @Test
  public void testDoesntFailOnDuplicateEntries() throws IOException {
    // `null` is serialized as `"null"`, but `null != "null"`, so we may have several "null" strings in the file. In production.
    // Make sure that even if we have duplicate items in the file, we still can handle them properly
    Path enumPath = myTempDirectory.newDirectoryPath().resolve("test.enum");
    Files.writeString(enumPath, "null\nnull");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    Assert.assertEquals("There is only one value: 'null', but two indexes for that value: 1 and 2", 1, enumerator.getSize());
    int id = enumerator.enumerate("one");
    Assert.assertEquals("1=null,2=null, this is third line", 3, id);
  }
}
