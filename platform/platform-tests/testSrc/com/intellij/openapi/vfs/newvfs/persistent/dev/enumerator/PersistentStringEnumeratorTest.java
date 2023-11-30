// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Just to compare 'classic' strict enumerator against non-strict
 */
public class PersistentStringEnumeratorTest extends StringEnumeratorTestBase<PersistentStringEnumerator> {

  public PersistentStringEnumeratorTest() {
    super(/*valuesToTestOn: */ 1_000_000);
  }

  @Override
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    throw new AssumptionViolatedException("Not satisfied now -- need to investigate");
    //super.nullValue_EnumeratedTo_NULL_ID();
  }

  @Override
  protected PersistentStringEnumerator openEnumeratorImpl(final @NotNull Path storagePath) throws IOException {
    return new PersistentStringEnumerator(storagePath);
  }

  @Test
  public void stringsAboutTheSizeOfAppendBuffer_AreStillEnumeratedCorrectly() throws Exception {
    int minSize = AppendableStorageBackedByResizableMappedFile.APPEND_BUFFER_SIZE - 10;
    int maxSize = AppendableStorageBackedByResizableMappedFile.APPEND_BUFFER_SIZE * 2;

    String[] largeStrings = generateUniqueValues(10_000, minSize, maxSize);

    //check serialized strings are indeed all >minSize
    for (String string : largeStrings) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      EnumeratorStringDescriptor.INSTANCE.save(new DataOutputStream(baos), string);
      assertTrue(baos.size() > minSize);
    }

    Int2ObjectMap<String> enumeratorSnapshot = new Int2ObjectOpenHashMap<>();
    for (String string : largeStrings) {
      int id = enumerator.enumerate(string);
      enumeratorSnapshot.put(id, string);
    }

    for (Int2ObjectMap.Entry<String> entry : enumeratorSnapshot.int2ObjectEntrySet()) {
      int id = entry.getIntKey();
      String value = entry.getValue();

      String actualValue = enumerator.valueOf(id);
      assertEquals("id = " + id, value, actualValue);
    }

    for (Int2ObjectMap.Entry<String> entry : enumeratorSnapshot.int2ObjectEntrySet()) {
      int id = entry.getIntKey();
      String value = entry.getValue();

      assertEquals(id, enumerator.tryEnumerate(value));
    }
  }
}
