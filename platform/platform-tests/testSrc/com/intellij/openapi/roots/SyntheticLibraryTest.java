// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;

import java.util.Arrays;

public class SyntheticLibraryTest extends BasePlatformTestCase {
  public void testEquality() {
    VirtualFile file1 = myFixture.configureByText("file1.txt", "").getVirtualFile();
    VirtualFile file2 = myFixture.configureByText("file2.txt", "").getVirtualFile();
    SyntheticLibrary lib1 = SyntheticLibrary.newImmutableLibrary(Arrays.asList(file1, file2));
    SyntheticLibrary lib2 = SyntheticLibrary.newImmutableLibrary(Arrays.asList(file1, file2));
    SyntheticLibrary lib3 = SyntheticLibrary.newImmutableLibrary(Arrays.asList(file2, file1));
    Assert.assertEquals(lib1, lib2);
    Assert.assertNotEquals(lib1, lib3);
  }
}
