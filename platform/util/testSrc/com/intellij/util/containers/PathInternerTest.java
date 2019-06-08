// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/**
 * @author peter
 */
public class PathInternerTest extends TestCase {
  PathInterner.PathEnumerator interner = new PathInterner.PathEnumerator();

  public void testAddTwice() {
    assertEquals(interner.addPath("/foo/bar"), interner.addPath("/foo/bar"));
  }

  public void testAddDifferent() {
    assertNotSame(interner.addPath("/foo/bar"), interner.addPath("/foo/foo"));
  }

  public void testRetrieve() {
    int idx = interner.addPath("/foo/bar");
    int idx2 = interner.addPath("/foo/foo");
    int idx3 = interner.addPath("/foo");
    assertEquals("/foo/bar", interner.retrievePath(idx));
    assertEquals("/foo/foo", interner.retrievePath(idx2));
    assertEquals("/foo", interner.retrievePath(idx3));
  }

  public void testRetrieveNotExistingFails() {
    try {
      interner.retrievePath(239);
    }
    catch (IllegalArgumentException e) {
      return;
    }
    fail();
  }

  public void testContains() {
    String path = "/home/peter/work/idea/community/out/production/intellij.platform.vcs.impl/com/intellij/openapi/vcs/changes/committed/CommittedChangesViewManager$1.class";
    interner.addPath(path);
    assertTrue(interner.containsPath(path));
    assertFalse(interner.containsPath("/foo/foo"));
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void main(String[] args) throws IOException {
    final java.util.HashSet<String> hs = new java.util.HashSet<>();
    FileUtil.processFilesRecursively(new File(PathManager.getHomePath()), file -> {
      hs.add(file.getPath());
      return true;
    });
    THashSet<String> thm = new THashSet<>();
    PathInterner.PathEnumerator interner = new PathInterner.PathEnumerator();
    for (String s : hs) {
      thm.add(s);
      if (!thm.contains(s)) {
        throw new AssertionError();
      }
      interner.addPath(s);
      if (!interner.containsPath(s)) {
        throw new AssertionError(s);
      }
    }
    System.out.println("Map collected, press when ready");

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    reader.readLine();

    System.out.println("Filling THashSet...");
    long start = System.currentTimeMillis();
    checkTrove(hs, thm);
    System.out.println("done " + (System.currentTimeMillis() - start));

    System.out.println("Filling PathInterner...");
    start = System.currentTimeMillis();
    checkInterner(hs, interner);
    System.out.println("done " + (System.currentTimeMillis() - start));
    hs.clear();
    System.out.println("press when ready");

    reader.readLine();

    System.out.println("interner.hashCode() = " + interner.hashCode());
    System.out.println("thm.hashCode() = " + thm.hashCode());
  }

  private static void checkInterner(HashSet<String> hs, PathInterner.PathEnumerator interner) {
    for (String s : hs) {
      if (!interner.containsPath(s)) {
        throw new AssertionError(new String(s));
      }
    }
  }

  private static void checkTrove(java.util.HashSet<String> hs, THashSet<String> thm) {
    for (String s : hs) {
      if (!thm.contains(new String(s))) {
        throw new AssertionError();
      }
    }
  }
}
