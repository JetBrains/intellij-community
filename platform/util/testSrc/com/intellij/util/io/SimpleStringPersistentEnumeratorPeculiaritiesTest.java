// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

/** Test for non-standard parts of {@link SimpleStringPersistentEnumerator} behavior */
public class SimpleStringPersistentEnumeratorPeculiaritiesTest {
  @Rule
  public TempDirectory tempDirectory = new TempDirectory();

  @Test
  public void enumeratorCreatesMissedDirectoryAndFiles() {
    Path enumPath = tempDirectory.newDirectoryPath().resolve("test-dir").resolve("test-enumerator");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    int id = enumerator.enumerate("anything");//no exceptions
  }

  @Test
  public void enumeratorFails_IfFileRemoveUnderTheFeet() throws IOException {
    Path enumPath = tempDirectory.newDirectoryPath().resolve("test-dir").resolve("test-enumerator");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    int id = enumerator.enumerate("anything");//no exceptions

    Files.delete(enumPath);

    assertThrows(
      "Enumerator must throw exception: file was removed during operations, it is exceptional case",
      UncheckedIOException.class,
      () -> {
        enumerator.enumerate("another anything");
      }
    );
  }

  @Test
  public void nullValueEnumeratesToValidId() {
    //TODO RC: other enumerators return NULL_ID(=0) for null -- is it really important to return non-NULL_ID
    //         value here?
    Path enumPath = tempDirectory.newDirectoryPath().resolve("test.enum");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    int id = enumerator.enumerate(null);
    assertEquals(1, id);
  }

  @Test
  public void enumeratorRejectsMultilineValues() {
    Path enumPath = tempDirectory.newDirectoryPath().resolve("test.enum");
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
  public void enumeratorAbleToReadDuplicatedEntriesFrom() throws IOException {
    // `null` is serialized as `"null"`, but `null != "null"`, so we may have several "null" strings in the file.
    // In production.
    // Make sure that even if we have duplicate items in the file, we still can handle them properly
    Path enumPath = tempDirectory.newDirectoryPath().resolve("test.enum");
    Files.writeString(enumPath, "null\nnull");
    SimpleStringPersistentEnumerator enumerator = new SimpleStringPersistentEnumerator(enumPath);
    //TODO RC: it is quite strange to have size=1 but nextId=3 -- much less surprising if nextId = size+1
    //         It is easy to change current .getSize() semantics, since the only .getSize() use other from here is
    //         .keysCountApproximately() which is by definition just an approximation, so don't bother if size +/- 1
    assertEquals("There is only one value: 'null', but two indexes for that value: 1 and 2", 1, enumerator.getSize());
    int id = enumerator.enumerate("one");
    assertEquals("1=null,2=null, 'one' must be the third line", 3, id);
  }
}
