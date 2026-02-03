// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.storage.Storage;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.blobstorage.StreamlinedBlobStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Test for 'classic' {@link Storage} implementation, with RecordTable
 */
public class RecordTableBasedBlobStorageTest extends BlobStorageTestBase<Storage> {

  @Override
  protected Storage openStorage(final Path pathToStorage) throws IOException {
    return new Storage(pathToStorage);
  }

  @Override
  protected void closeStorage(final Storage storage) throws Exception {
    Disposer.dispose(storage);
  }

  @Override
  protected boolean hasRecord(final Storage storage,
                              final int recordId) throws Exception {
    throw new UnsupportedOperationException("Method not implemented yet");
    //return storage.readStream(recordId).available() > 0;
  }

  @Override
  protected StorageRecord readRecord(final Storage storage,
                                     final int recordId) throws Exception {
    try (final DataInputStream dataStream = storage.readStream(recordId)) {
      final String payload = new String(dataStream.readAllBytes(), US_ASCII);
      return new StorageRecord(recordId, payload);
    }
  }

  @Override
  protected StorageRecord writeRecord(final StorageRecord record,
                                      final Storage storage) throws Exception {
    final String payload = record.payload;
    final int recordId = record.recordId;
    final ByteArraySequence bytes = new ByteArraySequence(payload.getBytes(US_ASCII));
    if (recordId == NULL_ID) {
      final int newRecordId = storage.createNewRecord();
      storage.writeBytes(
        newRecordId,
        bytes,
        false
      );
      return new StorageRecord(newRecordId, payload);
    }
    else {
      storage.writeBytes(
        recordId,
        bytes,
        false
      );
      return record;
    }
  }

  @Override
  protected void deleteRecord(final int recordId,
                              final Storage storage) throws Exception {
    storage.deleteRecord(recordId);
  }

  //@Test
  //public void newStorageHasVersionOfCurrentPersistentFormat() throws IOException {
  //  assertEquals(
  //    storage.getVersion(),
  //    Storage.VERSION_CURRENT
  //  );
  //}


  //TODO write/read records of size=0
  //TODO delete records
}