// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.NonParallelNonPersistentIntToMultiIntMap;
import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static com.intellij.util.io.DataEnumeratorEx.NULL_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DurableEnumeratorOfStringsTest extends StringEnumeratorTestBase<DurableEnumerator<String>> {

  @Test
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    int id = enumerator.enumerate(null);
    assertEquals(
      "null value enumerated to NULL_ID",
      NULL_ID,
      id
    );
  }

  @Test
  public void valueOf_NULL_ID_IsNull() throws IOException {
    String value = enumerator.valueOf(NULL_ID);
    assertNull(
      "valueOf(NULL_ID(=0)) must be null",
      value
    );
  }

  @Override
  protected DurableEnumerator<String> openEnumerator(@NotNull Path storagePath) throws IOException {
    return DurableEnumerator.open(
      storagePath,
      new KeyDescriptorEx<>() {
        @Override
        public int hashCodeOf(String value) {
          return value.hashCode();
        }

        @Override
        public boolean areEqual(String key1, String key2) {
          return key1.equals(key2);
        }

        @Override
        public String read(@NotNull ByteBuffer input) throws IOException {
          return IOUtil.readString(input);
        }

        @Override
        public long saveToLog(String key,
                              @NotNull AppendOnlyLog log) throws IOException {
          byte[] stringBytes = key.getBytes(UTF_8);
          return log.append(stringBytes);
        }

        @Override
        public int sizeOfSerialized(String key) throws IOException {
          throw new UnsupportedOperationException("Method not implemented");
        }

        @Override
        public void save(@NotNull ByteBuffer output,
                         String key) throws IOException {
          throw new UnsupportedOperationException("Method not implemented");
        }
      },
      NonParallelNonPersistentIntToMultiIntMap::new
    );
  }
}