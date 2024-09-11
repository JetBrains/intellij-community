// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import static org.junit.jupiter.api.Assertions.*;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes;
import com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes.TimestampedBooleanAttributeAccessor;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;


public class FastFileAttributesTest {

  private static final FileAttribute ATTRIBUTE = new FileAttribute("TEST_TIMESTAMPED_BOOLEAN_ATTRIBUTE");
  public static final int MANY_VALUES = 1 << 20;

  private FSRecordsImpl vfs;
  private TimestampedBooleanAttributeAccessor attributeAccessor;

  @BeforeEach
  public void setup(@TempDir Path vfsDir) throws IOException {
    vfs = FSRecordsImpl.connect(vfsDir);
    vfs.createRecord();//ensure root record created, just in case
    attributeAccessor = FastFileAttributes.timestampedBoolean(vfs, ATTRIBUTE);
  }

  @AfterEach
  public void tearDown() throws Exception {
    StorageTestingUtils.bestEffortToCloseAndClean(vfs);
  }

  @Test
  public void defaultValueIsNull() throws Exception {
    VFileMock vFile = createFile();
    assertNull(attributeAccessor.readIfActual(vFile),
               "Value is null by default -- i.e. if not written yet");
  }

  @Test
  public void singleValueWritten_MustBeReadBack_IfFileUnchanged() throws Exception {
    VFileMock vFile = createFile();

    attributeAccessor.write(vFile, true);
    assertTrue(attributeAccessor.readIfActual(vFile),
               "Value written must be read back if file unchanged");

    attributeAccessor.write(vFile, false);
    assertFalse(attributeAccessor.readIfActual(vFile),
                "Value written must be read back if file unchanged");
  }

  @Test
  public void singleValueWritten_ReadsBackAsDefaultNull_IfFileTimestampWasChanged() throws Exception {
    VFileMock vFile = createFile();

    attributeAccessor.write(vFile, true);
    assertTrue(attributeAccessor.readIfActual(vFile),
               "Value written must be read back if file unchanged");

    VFileMock vFileChanged = createFile(vFile.id, vFile.timestamp + 1);
    assertNull(attributeAccessor.readIfActual(vFileChanged),
               "File was changed -> previously written value is discarded, read as null (default)");
  }


  @Test
  public void defaultValueIsNull_forManyValues() throws Exception {
    List<VFileMock> manyFiles = Stream.generate(() -> createFile())
      .limit(MANY_VALUES)
      .toList();

    for (VFileMock vFile : manyFiles) {
      assertNull(attributeAccessor.readIfActual(vFile),
                 "Default value is null");
    }
  }

  @Test
  public void manyValuesWritten_MustBeReadBack_IfFileUnchanged() throws Exception {
    List<VFileMock> manyFiles = Stream.generate(() -> createFile())
      .limit(MANY_VALUES)
      .toList();

    for (VFileMock vFile : manyFiles) {
      boolean value = vFile.id % 2 == 1;
      attributeAccessor.write(vFile, value);
      assertEquals(value,
                   attributeAccessor.readIfActual(vFile),
                   "Value written must be read back if file unchanged");
    }

    for (VFileMock vFile : manyFiles) {
      boolean expectedValue = vFile.id % 2 == 1;
      assertEquals(expectedValue,
                   attributeAccessor.readIfActual(vFile),
                   "Value written must be read back if file unchanged");
    }
  }


  private @NotNull VFileMock createFile() {
    return createFile(System.currentTimeMillis());
  }

  private @NotNull VFileMock createFile(long timestamp) {
    int fileId = vfs.createRecord();
    return new VFileMock(fileId, timestamp);
  }

  private @NotNull VFileMock createFile(int fileId, long timestamp) {
    while (fileId > vfs.connection().records().maxAllocatedID()) {
      vfs.createRecord();
    }
    return new VFileMock(fileId, timestamp);
  }

  private static class VFileMock extends VirtualFile implements VirtualFileWithId {
    private final int id;
    private final long timestamp;

    VFileMock(int id, long timestamp) {
      this.id = id;
      this.timestamp = timestamp;
    }

    @Override
    public long getTimeStamp() {
      return timestamp;
    }

    @Override
    public int getId() {
      return id;
    }


    @Override
    public @NotNull String getName() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public @NotNull String getPath() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public boolean isWritable() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public boolean isDirectory() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public boolean isValid() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public VirtualFile getParent() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public VirtualFile[] getChildren() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public byte @NotNull [] contentsToByteArray() throws IOException {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public long getLength() {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
      throw new UnsupportedOperationException("Method is not implemented");
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      throw new UnsupportedOperationException("Method is not implemented");
    }
  }
}