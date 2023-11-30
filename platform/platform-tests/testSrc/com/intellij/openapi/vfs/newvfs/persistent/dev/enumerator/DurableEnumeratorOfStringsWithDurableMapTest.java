// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.StorageTestingUtils;
import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class DurableEnumeratorOfStringsWithDurableMapTest extends StringEnumeratorTestBase<DurableEnumerator<String>> {

  public DurableEnumeratorOfStringsWithDurableMapTest() {
    super(/*valuesToTestOn: */ 1_000_000);
  }

  @Test
  public void imProperlyClosedEnumerator_stillKeepsEnumeratedValue() throws Exception {
    String value = "anything";
    int valueId = enumerator.enumerate(value);
    StorageTestingUtils.emulateImproperClose(enumerator);

    enumerator = openEnumerator(storageFile);
    assertEquals(
      valueId,
      enumerator.tryEnumerate(value)
    );
    assertEquals(
      value,
      enumerator.valueOf(valueId)
    );
  }

  @Test
  public void closeAndClean_RemovesTheStorageFile() throws IOException {
    //RC: it is over-specification -- .closeAndClean() doesn't require to remove the file, only to clean the
    //    content so new storage opened on top of it will be as-new. But this is the current implementation
    //    of that spec:
    enumerator.closeAndClean();
    String name = storageFile.getFileName().toString();
    try (Stream<Path> filesInDir = Files.list(storageFile.getParent())) {
      assertFalse(
        filesInDir.anyMatch(p -> p.getFileName().toString().startsWith(name)),
        "No storage files [" + storageFile + "*] should exist after .closeAndClean()"
      );
    }
  }

  @Override
  protected DurableEnumerator<String> openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return DurableEnumeratorFactory.defaultWithDurableMap(StringAsUTF8.INSTANCE)
      .open(storagePath);
  }
}