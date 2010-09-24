/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.util.io.PagePool;

import java.io.File;
import java.io.IOException;

class RecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 1;

  private static final byte[] ZEROES = new byte[DEFAULT_RECORD_SIZE];

  RecordsTable(File storageFilePath, PagePool pool) throws IOException {
    super(storageFilePath, pool);
  }

  @Override
  protected int getImplVersion() {
    return VERSION;
  }

  @Override
  protected int getRecordSize() {
    return DEFAULT_RECORD_SIZE;
  }

  @Override
  protected byte[] getZeros() {
    return ZEROES;
  }

}
