// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.enumerator;

import com.intellij.util.io.*;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
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

  private StorageLockContext recentLockContext;

  public PersistentStringEnumeratorTest() {
    super(/*valuesToTestOn: */ 1_000_000);
  }

  @Override
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    throw new AssumptionViolatedException("Not satisfied now -- need to investigate");
    //super.nullValue_EnumeratedTo_NULL_ID();
  }

  @Override
  @Ignore
  public void runningMultiThreaded_valuesListedByForEach_alwaysKnownToTryEnumerate() throws Exception {
    throw new AssumptionViolatedException("Not satisfied now -- need to investigate");
  }

  @Override
  protected PersistentStringEnumerator openEnumeratorImpl(final @NotNull Path storagePath) throws IOException {
    recentLockContext = new StorageLockContext(true, true, false);
    return new PersistentStringEnumerator(storagePath, recentLockContext);
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



  //RUBY-32416:

  @Test
  public void markCorrupted_IsNotDeadlocking_IfCalledWithStorage_Read_LockAcquired() {
    recentLockContext.lockRead();
    try {
      int readLockHoldsBefore = recentLockContext.readLockHolds();
      enumerator.markCorrupted();

      int readLockHoldsAfter = recentLockContext.readLockHolds();
      assertEquals("Number of storage.readLock acquisitions must not change",
                   readLockHoldsBefore, readLockHoldsAfter);
    }
    finally {
      recentLockContext.unlockRead();
    }
  }

  @Test
  public void markCorrupted_IsNotDeadlocking_IfCalledWith_Few_Storage_Read_LocksAcquired() {
    recentLockContext.lockRead();
    try {
      recentLockContext.lockRead();
      try {
        recentLockContext.lockRead();
        try {
          int readLockHoldsBefore = recentLockContext.readLockHolds();
          enumerator.markCorrupted();

          int readLockHoldsAfter = recentLockContext.readLockHolds();
          assertEquals("Number of storage.readLock acquisitions must not change",
                       readLockHoldsBefore, readLockHoldsAfter);
        }
        finally {
          recentLockContext.unlockRead();
        }
      }
      finally {
        recentLockContext.unlockRead();
      }
    }
    finally {
      recentLockContext.unlockRead();
    }
  }

  @Test
  public void markCorrupted_IsNotDeadlocking_IfCalledWithStorage_Write_LockAcquired() {
    recentLockContext.lockWrite();
    try {
      int readLockHoldsBefore = recentLockContext.readLockHolds();
      enumerator.markCorrupted();

      int readLockHoldsAfter = recentLockContext.readLockHolds();
      assertEquals("Number of storage.readLock acquisitions must not change",
                   readLockHoldsBefore, readLockHoldsAfter);
    }
    finally {
      recentLockContext.unlockWrite();
    }
  }
}
