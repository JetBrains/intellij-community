// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import junit.framework.TestCase;

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
}
