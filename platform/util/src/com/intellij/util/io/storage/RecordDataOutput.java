// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.io.storage;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author max
 */ 
public interface RecordDataOutput extends DataOutput {
  int getRecordId();
  void close() throws IOException;
}
