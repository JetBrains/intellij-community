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
import static junit.framework.TestCase.assertFalse;

public class JdkBundleTest {

  private static final Version JDK6_VERSION = new Version(1, 6, 0);
  private static final Version JDK7_VERSION = new Version(1, 7, 0);

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String STANDARD_JDK_6_LOCATION_ON_MAC_OS_X = "/System/Library/Java/JavaVirtualMachines/";

  private static File[] findJdkInDirectory (File locationToSearch, String version) {
    return locationToSearch.listFiles(pathname -> pathname.getName().contains(version));
  }

  @Test
  public void testJdk6OnMac() {
    if (!SystemInfo.isMac) return;

    boolean testPassed;

    File [] jdk6Files = null;


    File standardJdk6LocationDirectory = new File(STANDARD_JDK_6_LOCATION_ON_MAC_OS_X);

    if (standardJdk6LocationDirectory.exists()) {
        jdk6Files = findJdkInDirectory(standardJdk6LocationDirectory, "1.6.0");
    }

    if (jdk6Files == null || jdk6Files.length == 0) {
      File standardJdkLocationDirectory = new File(STANDARD_JDK_LOCATION_ON_MAC_OS_X);
      jdk6Files = findJdkInDirectory(standardJdkLocationDirectory, "1.6.0");
    }

    if (jdk6Files == null || jdk6Files.length == 0) {
      // We have not found any jdk6 installation. Nothing to test.
      return;
    }

    JdkBundleList jdkBundleList = new JdkBundleList();
    jdkBundleList.addBundlesFromLocation(STANDARD_JDK_6_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);
    jdkBundleList.addBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);

    ArrayList<JdkBundle> bundles = jdkBundleList.toArrayList();

    for (File file : jdk6Files) {
      testPassed = false;
      for (JdkBundle bundle : bundles) {
        if (FileUtil.filesEqual(bundle.getLocation(), file)) {
          testPassed = true;
          break;
        }
      }
      assertTrue(file.getAbsolutePath() + " has not been found among jdk bundles.", testPassed);
    }
  }

  @Test
  public void testJre7OnMac() {
    if (!SystemInfo.isMac || !"true".equals(System.getProperty("idea.jre.check"))) return;

    File standardJdkLocationDirectory = new File(STANDARD_JDK_LOCATION_ON_MAC_OS_X);
    File [] jre7Files = findJdkInDirectory(standardJdkLocationDirectory, "1.7.0");

    if (jre7Files == null || jre7Files.length == 0) return;

    boolean hasJre7 = false;
    for (File file : jre7Files) {
      if (!new File(file, "Contents/Home/lib/tools.jar").exists()) {
        hasJre7 = true;
        break;
      }
    }

    if (!hasJre7) return;

    JdkBundleList jdkBundleList = new JdkBundleList();
    jdkBundleList.addBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK7_VERSION, JDK7_VERSION);

    ArrayList<JdkBundle> bundles = jdkBundleList.toArrayList();

    for (JdkBundle bundle : bundles) {
      assertTrue("jre \"" + bundle.getLocation().getAbsolutePath() + "\" found among jdk bundles",
                  new File(bundle.getLocation(), "Contents/Home/lib/tools.jar").exists());
    }
  }

  public void doTestCreateBundle(File homeJDK) {
    if (!new File(homeJDK, "lib/tools.jar").exists()) return; // Skip pure jre

    File bootJDK = SystemInfo.isMac ? homeJDK.getParentFile().getParentFile() : homeJDK;
    String verStr = System.getProperty("java.version");

    boolean macNonStandardJDK = SystemInfo.isMac && !new File(bootJDK, "Contents/Home").exists();
    JdkBundle bundle = macNonStandardJDK
                       ? JdkBundle.createBundle(homeJDK, "", true, false, true) : // the test is run under jdk with non-standard layout
                       JdkBundle.createBundle(bootJDK, true, false);

    assertNotNull(bundle);

    assertTrue(bundle.isBoot());
    assertFalse(bundle.isBundled());

    assertTrue(FileUtil.filesEqual(bundle.getLocation(), macNonStandardJDK ? homeJDK : bootJDK));
    Pair<Version, Integer> verUpdate = bundle.getVersionUpdate();

    assertNotNull(verUpdate);

    final String evalVerStr = verUpdate.first.toString() + "_" + verUpdate.second.toString();
    assertTrue(evalVerStr + " is not the same with " + verStr, verStr.contains(evalVerStr));
  }

  @Test
  public void testCreateBundle() {
    File home = new File(System.getProperty("java.home"));

    doTestCreateBundle(home);

    doTestCreateBundle(home.getParentFile());
  }

  @Test
  public void testCreateBoot() {
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

    assertTrue(FileUtil.filesEqual(bundle.getLocation(), macNonStandardJDK ? homeJDK : bootJDK));
    Pair<Version, Integer> verUpdate = bundle.getVersionUpdate();

    assertNotNull(verUpdate);
    assertNotNull(bundle.getUpdateNumber());

    final String evalVerStr = verUpdate.first.toString() + "_" + verUpdate.second.toString();
    assertTrue(evalVerStr + " is not the same with " + verStr, verStr.contains(evalVerStr));
  }
}