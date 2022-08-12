// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import org.jetbrains.annotations.NotNull;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class PersistentInMemoryFSRecordsStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentInMemoryFSRecordsStorage> /*extends BareTestFixtureTestCase*/ {

  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  public PersistentInMemoryFSRecordsStorageTest() { super(MAX_RECORDS_TO_INSERT); }

  @NotNull
  protected PersistentInMemoryFSRecordsStorage openStorage(final File storageFile,
                                                           final int maxRecordsToInsert) {
    return new PersistentInMemoryFSRecordsStorage(storageFile.toPath(), maxRecordsToInsert);
  }
}