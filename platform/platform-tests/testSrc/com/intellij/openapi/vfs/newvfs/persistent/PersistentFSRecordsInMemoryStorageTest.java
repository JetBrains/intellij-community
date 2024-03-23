// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentFSRecordsInMemoryStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsOverInMemoryStorage> /*extends BareTestFixtureTestCase*/ {


  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  public PersistentFSRecordsInMemoryStorageTest(){
    super(MAX_RECORDS_TO_INSERT);
  }

  @NotNull
  @Override
  protected PersistentFSRecordsOverInMemoryStorage openStorage(Path storagePath) throws IOException {
    return new PersistentFSRecordsOverInMemoryStorage(storagePath, maxRecordsToInsert);
  }

  @Override
  @Ignore("InMemory storage doesn't use the file -> doesn't remove it either")
  public void closeAndRemoveAllFiles_cleansUpEverything_newStorageCreatedFromSameFilenameIsEmpty(){
  }
}