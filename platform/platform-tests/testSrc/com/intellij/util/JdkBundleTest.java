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
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.*;

public class JdkBundleTest {

  @Test
  public void testCreateBundle() throws Exception {
    if (SystemInfo.isWindows) return; // Windows is not supported so far
    File homeJDK = new File(System.getProperty("java.home")).getParentFile();

    if (!new File(homeJDK, "lib/tools.jar").exists()) return; // Skip pure jre

    File bootJDK = SystemInfo.isMac ? homeJDK.getParentFile().getParentFile() : homeJDK;
    String verStr = System.getProperty("java.version");

    boolean macNonStandardJDK = SystemInfo.isMac && !new File(bootJDK, "Contents/Home").exists();
    JdkBundle bundle = macNonStandardJDK
                       ? JdkBundle.createBundle(homeJDK, "", true, true) : // the test is run under jdk with non-standard layout
                       JdkBundle.createBundle(bootJDK, true, true);

    assertNotNull(bundle);

    assertTrue(bundle.isBoot());
    assertTrue(bundle.isBundled());

    assertTrue(FileUtil.filesEqual(bundle.getBundleAsFile(), macNonStandardJDK ? homeJDK : bootJDK));
    Pair<Version, Integer> verUpdate = bundle.getVersionUpdate();

    assertNotNull(verUpdate);

    assertEquals(verStr, verUpdate.first.toString() + "_" + verUpdate.second.toString());
  }

  @Test
  public void testCreateBoot() throws Exception {
    if (SystemInfo.isWindows) return; // Windows is not supported so far
    File homeJDK = new File(System.getProperty("java.home")).getParentFile();

    if (!new File(homeJDK, "lib/tools.jar").exists()) return; // Skip pure jre

    File bootJDK = SystemInfo.isMac ? homeJDK.getParentFile().getParentFile() : homeJDK;
    String verStr = System.getProperty("java.version");

    boolean macNonStandardJDK = SystemInfo.isMac && !new File(bootJDK, "Contents/Home").exists();
    JdkBundle bundle = macNonStandardJDK ? JdkBundle.createBoot(false) : // the test is run under jdk with non-standard layout
                       JdkBundle.createBoot();

    assertNotNull(bundle);
    assertTrue(bundle.isBoot());
    assertFalse(bundle.isBundled());

    assertTrue(FileUtil.filesEqual(bundle.getBundleAsFile(), macNonStandardJDK ? homeJDK : bootJDK));
    Pair<Version, Integer> verUpdate = bundle.getVersionUpdate();

    assertNotNull(verUpdate);

    assertEquals(verStr, verUpdate.first.toString() + "_" + verUpdate.second.toString());
  }
}