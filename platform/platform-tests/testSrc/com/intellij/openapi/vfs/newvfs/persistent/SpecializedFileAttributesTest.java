// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes.*;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class SpecializedFileAttributesTest {
  private static final FileAttribute TEST_LONG_ATTRIBUTE = new FileAttribute("TEST_ATTRIBUTE_LONG", 1, true);
  private static final FileAttribute TEST_INT_ATTRIBUTE = new FileAttribute("TEST_ATTRIBUTE_INT", 1, true);
  private static final FileAttribute TEST_SHORT_ATTRIBUTE = new FileAttribute("TEST_ATTRIBUTE_SHORT", 1, true);
  private static final FileAttribute TEST_BYTE_ATTRIBUTE = new FileAttribute("TEST_ATTRIBUTE_BYTE", 1, true);

  private static final int ENOUGH_VALUES = 1 << 20;

  private FSRecordsImpl vfs;

  private ByteFileAttributeAccessor byteAttributeAccessor;
  private ByteFileAttributeAccessor byteFastAttributeAccessor;

  private ShortFileAttributeAccessor shortAttributeAccessor;
  private ShortFileAttributeAccessor shortFastAttributeAccessor;

  private IntFileAttributeAccessor intAttributeAccessor;
  private IntFileAttributeAccessor intFastAttributeAccessor;

  private LongFileAttributeAccessor longAttributeAccessor;
  private LongFileAttributeAccessor longFastAttributeAccessor;

  private Map<Path, MappedFileStorageHelper> registeredStoragesBefore;


  @BeforeEach
  public void setup(@TempDir Path tempDir) throws Exception {
    registeredStoragesBefore = MappedFileStorageHelper.registeredStorages();

    vfs = FSRecordsImpl.connect(tempDir);

    byteAttributeAccessor = specializeAsByte(vfs, TEST_BYTE_ATTRIBUTE);
    byteFastAttributeAccessor = specializeAsFastByte(vfs, TEST_BYTE_ATTRIBUTE);

    shortAttributeAccessor = specializeAsShort(vfs, TEST_SHORT_ATTRIBUTE);
    shortFastAttributeAccessor = specializeAsFastShort(vfs, TEST_SHORT_ATTRIBUTE);

    intAttributeAccessor = specializeAsInt(vfs, TEST_INT_ATTRIBUTE);
    intFastAttributeAccessor = specializeAsFastInt(vfs, TEST_INT_ATTRIBUTE);

    longAttributeAccessor = specializeAsLong(vfs, TEST_LONG_ATTRIBUTE);
    longFastAttributeAccessor = specializeAsFastLong(vfs, TEST_LONG_ATTRIBUTE);
  }

  @AfterEach
  public void tearDown() throws Exception {
    StorageTestingUtils.bestEffortToCloseAndClean(vfs);

    //RC: Can't just check for .isEmpty(): if running in the same process with other tests -- could be storages
    //    registered by them
    Map<Path, MappedFileStorageHelper> registeredStoragesAfter = MappedFileStorageHelper.registeredStorages();
    assertEquals(
      registeredStoragesBefore,
      registeredStoragesAfter,
      "All storages opened during the test -- must be closed and de-registered during VFS close: \n" +
      "before:" + registeredStoragesAfter.keySet().stream().map(p -> p.toString())
        .collect(joining("\n\t", "\n", "\n")) +
      "\nafter:" + registeredStoragesAfter.keySet().stream().map(p -> p.toString())
        .collect(joining("\n\t", "\n", "\n"))
    );
  }

  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void singleIntValue_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    IntFileAttributeAccessor accessor = testFastAccessor ? intFastAttributeAccessor : intAttributeAccessor;
    int fileId = vfs.createRecord();
    int valueToWrite = 1234;
    accessor.write(fileId, valueToWrite);
    int readBack = accessor.read(fileId, 0);
    assertEquals(valueToWrite, readBack,
                 "Value written must be read back as is");
  }

  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void manyIntValues_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    IntFileAttributeAccessor accessor = testFastAccessor ? intFastAttributeAccessor : intAttributeAccessor;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int fileId = vfs.createRecord();
      int valueToWrite = rnd.nextInt();
      accessor.write(fileId, valueToWrite);
      int readBack = accessor.read(fileId, 0);
      assertEquals(valueToWrite, readBack,
                   "Value written must be read back as is");
    }
  }

  @Test
  public void manyIntValues_CouldBeWrittenViaAccessor_AndReadBackAsIs_ViaRegularReadAttribute() throws Exception {
    IntFileAttributeAccessor accessor = intAttributeAccessor;
    int fileId = vfs.createRecord();
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int valueToWrite = rnd.nextInt();
      accessor.write(fileId, valueToWrite);
      try (var stream = vfs.readAttribute(fileId, TEST_INT_ATTRIBUTE)) {
        int readBack = stream.readInt();
        assertEquals(valueToWrite, readBack,
                     "Value written via attributeAccessor must be read back as is via regular readAttribute()");
      }
    }
  }


  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void singleShortValue_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    ShortFileAttributeAccessor accessor = testFastAccessor ? shortFastAttributeAccessor : shortAttributeAccessor;
    int fileId = vfs.createRecord();
    short valueToWrite = 1234;
    accessor.write(fileId, valueToWrite);
    short readBack = accessor.read(fileId, (short)0);
    assertEquals(valueToWrite, readBack,
                 "Value written must be read back as is");
  }

  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void manyShortValues_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    ShortFileAttributeAccessor accessor = testFastAccessor ? shortFastAttributeAccessor : shortAttributeAccessor;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int fileId = vfs.createRecord();
      short valueToWrite = (short)rnd.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
      accessor.write(fileId, valueToWrite);
      short readBack = accessor.read(fileId, (short)0);
      assertEquals(valueToWrite, readBack,
                   "Value written must be read back as is");
    }
  }

  @Test
  public void manyShortValues_CouldBeWrittenViaAccessor_AndReadBackAsIs_ViaRegularReadAttribute() throws Exception {
    ShortFileAttributeAccessor accessor = shortAttributeAccessor;
    int fileId = vfs.createRecord();
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      short valueToWrite = (short)rnd.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
      accessor.write(fileId, valueToWrite);
      try (var stream = vfs.readAttribute(fileId, TEST_SHORT_ATTRIBUTE)) {
        short readBack = stream.readShort();
        assertEquals(valueToWrite, readBack,
                     "Value written via attributeAccessor must be read back as is via regular readAttribute()");
      }
    }
  }


  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void singleLongValue_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    LongFileAttributeAccessor accessor = testFastAccessor ? longFastAttributeAccessor : longAttributeAccessor;
    int fileId = vfs.createRecord();
    long valueToWrite = 1234;
    accessor.write(fileId, valueToWrite);
    long readBack = accessor.read(fileId, 0);
    assertEquals(valueToWrite, readBack,
                 "Value written must be read back as is");
  }

  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void manyLongValues_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    LongFileAttributeAccessor accessor = testFastAccessor ? longFastAttributeAccessor : longAttributeAccessor;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int fileId = vfs.createRecord();
      long valueToWrite = rnd.nextLong();

      accessor.write(fileId, valueToWrite);

      long readBack = accessor.read(fileId, 0);
      assertEquals(valueToWrite, readBack,
                   "Value written must be read back as is");
    }
  }

  @Test
  public void manyLongValues_CouldBeWrittenViaAccessor_AndReadBackAsIs_ViaRegularReadAttribute() throws Exception {
    LongFileAttributeAccessor accessor = longAttributeAccessor;
    int fileId = vfs.createRecord();
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      long valueToWrite = rnd.nextLong();
      accessor.write(fileId, valueToWrite);
      try (var stream = vfs.readAttribute(fileId, TEST_LONG_ATTRIBUTE)) {
        long readBack = stream.readLong();
        assertEquals(valueToWrite, readBack,
                     "Value written via attributeAccessor must be read back as is via regular readAttribute()");
      }
    }
  }


  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void singleByteValue_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    ByteFileAttributeAccessor accessor = testFastAccessor ? byteFastAttributeAccessor : byteAttributeAccessor;
    int fileId = vfs.createRecord();
    byte valueToWrite = 123;
    accessor.write(fileId, valueToWrite);
    byte readBack = accessor.read(fileId, (byte)0);
    assertEquals(valueToWrite, readBack,
                 "Value written must be read back as is");
  }

  @ParameterizedTest(name = "fast: {0}")
  @ValueSource(booleans = {true, false})
  public void manyByteValues_CouldBeWritten_AndReadBackAsIs(boolean testFastAccessor) throws Exception {
    ByteFileAttributeAccessor accessor = testFastAccessor ? byteFastAttributeAccessor : byteAttributeAccessor;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int fileId = vfs.createRecord();
      byte valueToWrite = (byte)rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);

      accessor.write(fileId, valueToWrite);

      byte readBack = accessor.read(fileId, (byte)0);
      assertEquals(valueToWrite, readBack,
                   "Value written must be read back as is");
    }
  }
}