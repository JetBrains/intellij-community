// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.util.io.CleanableStorage;

import java.io.IOException;

public interface RefCountingContentStorage extends IStorage, CleanableStorage {
  int acquireNewRecord() throws IOException;

  int getRecordsCount() throws IOException;

  void acquireRecord(int record) throws IOException;

  void releaseRecord(int record) throws IOException;

  int getRefCount(int record) throws IOException;
}
