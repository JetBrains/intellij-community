// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

final class PersistentFSHeaders {
  static final int HEADER_VERSION_OFFSET = 0;
  //private static final int HEADER_RESERVED_4BYTES_OFFSET = 4; // reserved
  static final int HEADER_GLOBAL_MOD_COUNT_OFFSET = 8;
  static final int HEADER_TIMESTAMP_OFFSET = 16;
  static final int HEADER_SIZE = HEADER_TIMESTAMP_OFFSET + 8;
  static final int HEADER_CONNECTION_STATUS_OFFSET = 12;

  static final int CONNECTED_MAGIC = 0x12ad34e4;
  static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;
  static final int CORRUPTED_MAGIC = 0xabcf7f7f;

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= PersistentFSRecordsStorage.RECORD_SIZE;
  }
}
