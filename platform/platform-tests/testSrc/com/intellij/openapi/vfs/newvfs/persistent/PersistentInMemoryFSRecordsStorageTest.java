// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentInMemoryFSRecordsStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentInMemoryFSRecordsStorage> /*extends BareTestFixtureTestCase*/ {


  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  public PersistentInMemoryFSRecordsStorageTest(){
    super(MAX_RECORDS_TO_INSERT);
  }

  @NotNull
  @Override
  protected PersistentInMemoryFSRecordsStorage openStorage(Path storagePath) throws IOException {
    return new PersistentInMemoryFSRecordsStorage(storagePath, maxRecordsToInsert);
  }

  @Override
  @Ignore("InMemory storage doesn't use the file -> doesn't remove it either")
  public void closeAndRemoveAllFiles_cleansUpEverything_newStorageCreatedFromSameFilenameIsEmpty(){
  }
}