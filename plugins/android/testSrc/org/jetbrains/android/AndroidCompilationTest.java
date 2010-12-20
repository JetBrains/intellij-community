/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.HashSet;
import junit.framework.TestCase;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCompilationTest extends TestCase {
  private static String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/plugins/android/testData/compilation";
  }

  public void testCollectDuplicates1() throws Exception {
    HashSet<String> entries = new HashSet<String>();
    HashSet<String> duplicates = new HashSet<String>();
    AndroidApkBuilder.collectDuplicateEntries(getTestDataPath() + "/jar1.jar", entries, duplicates);
    AndroidApkBuilder.collectDuplicateEntries(getTestDataPath() + "/jar2.jar", entries, duplicates);
    assertEquals(1, duplicates.size());
  }

  public void testCollectDuplicates2() throws Exception {
    HashSet<String> entries = new HashSet<String>();
    HashSet<String> duplicates = new HashSet<String>();
    AndroidApkBuilder.collectDuplicateEntries(getTestDataPath() + "/jar1.jar", entries, duplicates);
    AndroidApkBuilder.collectDuplicateEntries(getTestDataPath() + "/jar3.jar", entries, duplicates);
    assertEquals(0, duplicates.size());
  }
}
