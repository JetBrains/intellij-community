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
import java.util.ArrayList;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.*;

public class JdkBundleTest {

  private static final Version JDK6_VERSION = new Version(1, 6, 0);

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String STANDARD_JDK_6_LOCATION_ON_MAC_OS_X = "/System/Library/Java/JavaVirtualMachines/";

  private static File findJdkInDirectory (File locationToSearch) {
    File[] files = locationToSearch.listFiles(pathname -> {
      return pathname.getName().contains("1.6.0");
    });
    if (files != null && files.length > 0) {
      return files[0];
    }
    return null;
  }

  @Test
  public void testJdk6OnMac() throws Exception {
    if (!SystemInfo.isMac) return;

    boolean testPassed = false;

    File jdk6File = null;


    File standardJdk6LocationDirectory = new File(STANDARD_JDK_6_LOCATION_ON_MAC_OS_X);

    if (standardJdk6LocationDirectory.exists()) {
        jdk6File = findJdkInDirectory(standardJdk6LocationDirectory);
    }

    if (jdk6File == null) {
      File standardJdkLocationDirectory = new File(STANDARD_JDK_LOCATION_ON_MAC_OS_X);
      jdk6File = findJdkInDirectory(standardJdkLocationDirectory);
    }

    if (jdk6File == null) {
      // We have not found any jdk6 installation. Nothing to test.
      return;
    }

    JdkBundleList jdkBundleList = new JdkBundleList();
    jdkBundleList.addBundlesFromLocation(STANDARD_JDK_6_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);
    jdkBundleList.addBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);

    ArrayList<JdkBundle> bundles = jdkBundleList.toArrayList();

    for (JdkBundle bundle : bundles) {
      if (FileUtil.filesEqual(bundle.getBundleAsFile(), jdk6File)) {
        testPassed = true;
        break;
      }
    }

    assertTrue(jdk6File.getAbsolutePath() + " has not been found among jdk bundles.", testPassed);

  }

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