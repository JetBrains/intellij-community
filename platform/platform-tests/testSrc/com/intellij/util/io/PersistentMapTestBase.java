/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
abstract class PersistentMapTestBase extends TestCase {
  protected static final Logger LOG = Logger.getInstance(PersistentMapTestBase.class);
  protected PersistentHashMap<String, String> myMap;
  protected File myFile;
  protected File myDataFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File directory = FileUtil.createTempDirectory("persistent", "map");
    myFile = new File(directory, "map");
    assertTrue(myFile.createNewFile());
    myDataFile = new File(directory, myFile.getName() + PersistentHashMap.DATA_FILE_EXTENSION);
    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
  }

  @Override
  protected void tearDown() throws Exception {
    clearMap(myFile, myMap);
    myMap = null;
    super.tearDown();
  }

  static void clearMap(final File file1, PersistentHashMap<?, ?> map) throws IOException {
    if (map == null) return;
    map.close();

    assertTrue(IOUtil.deleteAllFilesStartingWith(file1));
  }

  static String createRandomString() {
    return StringEnumeratorTest.createRandomString();
  }
}
