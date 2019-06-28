// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class PathInternerTest extends TestCase {
  private final PathInterner.PathEnumerator interner = new PathInterner.PathEnumerator();

  public void testAddTwice() {
    assertSame(interner.addPath("/foo/bar"), interner.addPath("/foo/bar"));
  }

  public void testAddDifferent() {
    assertNotSame(interner.addPath("/foo/bar"), interner.addPath("/foo/foo"));
  }

  public void testRetrieve() {
    int idx = interner.addPath("/foo/bar");
    int idx2 = interner.addPath("/foo/foo");
    int idx3 = interner.addPath("/foo");
    assertEquals("/foo/bar", interner.retrievePath(idx).toString());
    assertEquals("/foo/foo", interner.retrievePath(idx2).toString());
    assertEquals("/foo", interner.retrievePath(idx3).toString());
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
  public static void main(String[] args) {
    final java.util.HashSet<String> hs = new java.util.HashSet<>();
    FileUtil.processFilesRecursively(new File(PathManager.getHomePath()), file -> {
      hs.add(file.getPath());
      return true;
    });
    THashSet<String> thm = new THashSet<>();
    PathInterner.PathEnumerator interner = new PathInterner.PathEnumerator();
    for (String s : hs) {
      if (!thm.add(s) || !thm.contains(s)) {
        throw new AssertionError(s);
      }
      int i = interner.addPath(s);
      if (!interner.containsPath(s)) {
        throw new AssertionError(s);
      }
      CharSequence interned = interner.intern(s);
      assert interned == interner.retrievePath(i) : s;
      assert interned.hashCode() == s.hashCode() : s;
    }
    //System.out.println("Map collected, press when ready");

    //BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    //reader.readLine();

    System.out.println("Filling THashSet...");
    long start = System.currentTimeMillis();
    checkTrove(hs, thm);
    checkTrove(thm, hs);
    System.out.println("done " + (System.currentTimeMillis() - start));

    System.out.println("Filling PathInterner...");
    start = System.currentTimeMillis();
    checkInterner(hs, interner);
    checkInterner2(hs, interner);
    checkInterner(thm, interner);
    checkInterner2(thm, interner);
    System.out.println("done " + (System.currentTimeMillis() - start));
    //hs.clear();
    //System.out.println("press when ready");

    //reader.readLine();

    System.out.println("interner.size() = " + interner.getValues().size());
    System.out.println("thm.size() = " + thm.size());
    List<String> is = ContainerUtil.map(interner.getValues(), CharSequence::toString);
    Collections.sort(is);
    List<String> ts = new ArrayList<>(thm);
    Collections.sort(ts);
    assertEquals(is, ts);
  }

  private static void checkInterner(Set<String> hs, PathInterner.PathEnumerator interner) {
    for (String s : hs) {
      if (!interner.containsPath(s)) {
        throw new AssertionError(s);
      }
    }
  }
  private static void checkInterner2(Set<String> hs, PathInterner.PathEnumerator interner) {
    for (CharSequence value : interner.getValues()) {
      if (!hs.contains(value.toString())) {
        throw new AssertionError(value);
      }
    }
  }

  private static void checkTrove(Set<String> hs, Set<String> thm) {
    for (String s : hs) {
      if (!thm.contains(s)) {
        throw new AssertionError(s);
      }
    }
  }
}
