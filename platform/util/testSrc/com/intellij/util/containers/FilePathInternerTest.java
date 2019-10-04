// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.FilePathInterner;
import com.intellij.testFramework.LightPlatformTestCase;
import gnu.trove.THashSet;

import java.io.File;
import java.util.Set;

public class FilePathInternerTest extends LightPlatformTestCase {
  public void testHashCodeIsConsistentWithStringStress() {
    long start = System.currentTimeMillis();
    Set<String> thm = new THashSet<>();
    Set<CharSequence> uniq = new THashSet<>(ContainerUtil.identityStrategy());
    FilePathInterner interner = new FilePathInterner();
    FileUtil.processFilesRecursively(new File(PathManagerEx.getTestDataPath()), file -> {
      String s = FileUtil.toSystemIndependentName(file.getPath());

      if (!thm.add(s) || !thm.contains(s)) {
        throw new AssertionError(s);
      }
      CharSequence interned = interner.intern(s);
      boolean added = uniq.add(interned);
      assertTrue(s, added);
      assertEquals(s, interned.toString(), s);
      assertEquals(s, interned.hashCode(), s.hashCode());

      return true;
    });

    System.out.println("done in " + (System.currentTimeMillis() - start)/1000 + "s; size="+uniq.size());

    checkInterner(thm, interner, uniq);
    checkInterner2(thm, interner, uniq);
  }

  private static void checkInterner(Set<String> hs, FilePathInterner interner, Set<? extends CharSequence> uniq) {
    for (String s : hs) {
      CharSequence intern = interner.intern(s);
      if (!uniq.contains(intern)) {
        throw new AssertionError(s);
      }
    }
  }
  private static void checkInterner2(Set<String> hs, FilePathInterner interner, Set<? extends CharSequence> uniq) {
    for (CharSequence value : uniq) {
      if (!hs.contains(value.toString())) {
        throw new AssertionError(value);
      }
      if (interner.intern(value) != value) {
        throw new AssertionError(value);
      }
    }
  }
}
