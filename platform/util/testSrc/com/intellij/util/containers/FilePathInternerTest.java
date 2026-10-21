// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.FilePathInterner;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FilePathInternerTest {
  private static final Logger LOG = Logger.getInstance(FilePathInternerTest.class);

  @Test
  public void testHashCodeIsConsistentWithStringStress() {
    long start = System.currentTimeMillis();
    Set<String> thm = new HashSet<>();
    Set<CharSequence> uniq = Collections.newSetFromMap(new IdentityHashMap<>());
    FilePathInterner interner = new FilePathInterner();
    FileUtil.processFilesRecursively(new File(PathManagerEx.getTestDataPath()), file -> {
      String s = FileUtil.toSystemIndependentName(file.getPath());

      if (!thm.add(s) || !thm.contains(s)) {
        throw new AssertionError(s);
      }
      CharSequence interned = interner.intern(s);
      boolean added = uniq.add(interned);
      assertTrue(added, s);
      assertEquals(interned.toString(), s, s);
      assertEquals(interned.hashCode(), s.hashCode(), s);

      return true;
    });

    LOG.debug("done in " + (System.currentTimeMillis() - start)/1000 + "s; size="+uniq.size());

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
