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

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public abstract class StorageTestBase extends TestCase {
  protected Storage myStorage;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStorage = createStorage(getFileName());
  }

  @NotNull
  protected Storage createStorage(String fileName) throws IOException {
    return new Storage(fileName);
  }

  protected String getFileName() {
    return FileUtil.getTempDirectory() + File.separatorChar + getName();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myStorage);
    Storage.deleteFiles(getFileName());
    super.tearDown();
  }

  protected void appendNBytes(final int r, final int len) throws IOException {
    DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < len; i++) {
      out.write(0);
    }
    myStorage.readBytes(r);
  }
}