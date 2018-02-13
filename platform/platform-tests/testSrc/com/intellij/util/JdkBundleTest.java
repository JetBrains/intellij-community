// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;
import org.junit.Test;

import java.io.File;
import java.util.Objects;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class JdkBundleTest {
  @Test
  public void nonExistentPath() {
    assertNull(JdkBundle.createBundle(new File("/no/such/path")));
  }

  @Test
  public void testCreateBundle() {
    File home = new File(System.getProperty("java.home"));

    doTestBootBundle(JdkBundle.createBundle(home), false);

    if ("jre".equals(home.getName())) {
      doTestBootBundle(JdkBundle.createBundle(home.getParentFile()), false);
    }
  }

  @Test
  public void testCreateBoot() {
    doTestBootBundle(JdkBundle.createBoot(), true);
  }

  private static void doTestBootBundle(JdkBundle bundle, boolean boot) {
    assertNotNull(bundle);
    assertEquals(boot, bundle.isBoot());
    assertTrue(bundle.isJdk());
    assertTrue(bundle.isOperational());

    File home = new File(SystemProperties.getJavaHome());
    if ("jre".equals(home.getName())) home = home.getParentFile();
    if (SystemInfo.isMac && "Home".equals(home.getName())) home = home.getParentFile().getParentFile();
    assertEquals(home, bundle.getLocation());

    JavaVersion current = JavaVersion.current();
    JavaVersion actual = bundle.getBundleVersion();
    assertEquals(current.feature, actual.feature);
    assertEquals(current.minor, actual.minor);
    assertEquals(current.update, actual.update);

    Bitness expected = SystemInfo.is64Bit ? Bitness.x64 : Bitness.x32;
    assertEquals(expected, bundle.getBitness());
  }

  @Test
  public void testStandardMacOsBundles() {
    assumeTrue(SystemInfo.isMac);
    for (File vm : Objects.requireNonNull(new File("/Library/Java/JavaVirtualMachines").listFiles())) {
      if (new File(vm, "Contents/Home/bin/java").isFile()) {
        JdkBundle bundle = JdkBundle.createBundle(vm);
        assertNotNull(vm.getPath(), bundle);
        assertEquals(vm, bundle.getLocation());
        assertFalse(vm.getPath(), bundle.isBundled());
        assertFalse(vm.getPath(), bundle.isBoot());
        assertTrue(bundle.isOperational());
      }
    }
  }
}