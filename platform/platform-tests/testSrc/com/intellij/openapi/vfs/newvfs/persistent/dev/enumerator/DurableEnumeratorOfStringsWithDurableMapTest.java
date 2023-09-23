// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.StorageTestingUtils;
import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;


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

  @Override
  protected DurableEnumerator<String> openEnumerator(@NotNull Path storagePath) throws IOException {
    return DurableEnumeratorFactory.defaultWithDurableMap(StringAsUTF8.INSTANCE)
      .open(storagePath);
  }
}