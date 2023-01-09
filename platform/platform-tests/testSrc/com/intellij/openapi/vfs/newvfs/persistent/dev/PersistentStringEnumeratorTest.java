// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.*;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Just to compare 'classic' strict enumerator against non-strict
 */
public class PersistentStringEnumeratorTest extends NonStrictStringsEnumeratorTestBase<PersistentStringEnumerator>{
  @Override
  protected PersistentStringEnumerator openEnumerator(final @NotNull Path storagePath) throws IOException {
    return new PersistentStringEnumerator(storagePath);
  }

  @SuppressWarnings("ConstantValue")
  @Test
  public void testLongStringsEnumeration() throws Exception {
    int minSize = 4000;
    int maxSize = 7000;
    assertTrue(minSize < AppendableStorageBackedByResizableMappedFile.ourAppendBufferLength);
    assertTrue(maxSize > AppendableStorageBackedByResizableMappedFile.ourAppendBufferLength);
    String[] strings = generateValues(10000, minSize, maxSize);

    for (String string : strings) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      EnumeratorStringDescriptor.INSTANCE.save(new DataOutputStream(baos), string);
      assertTrue(baos.size() > minSize);
    }


    Int2ObjectMap<String> enumeratorSnapshot = new Int2ObjectOpenHashMap<>();
    for (String string : strings) {
      int id = enumerator.enumerate(string);
      enumeratorSnapshot.put(id, string);
    }

    for (Int2ObjectMap.Entry<String> entry : enumeratorSnapshot.int2ObjectEntrySet()) {
      int id = entry.getIntKey();
      String value = entry.getValue();

      String actualValue = enumerator.valueOf(id);
      if (actualValue == null) {
        System.out.println("123");
      }
      assertEquals("id = " + id, value, actualValue);
    }

    for (Int2ObjectMap.Entry<String> entry : enumeratorSnapshot.int2ObjectEntrySet()) {
      int id = entry.getIntKey();
      String value = entry.getValue();

      assertEquals(id, enumerator.tryEnumerate(value));
    }
  }
}
