/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import junit.framework.TestCase;

/**
 * @author Eugene Zhuravlev
 *         Date: May 19, 2007
 */
public class PatternsTest extends TestCase {
  public void testIncludesRecursively() {
    final AntPattern antPattern = new AntPattern(true);
    antPattern.addIncludePattern("**/source/**/*.java");
    
    assertTrue(antPattern.acceptPath("source/foo.java"));
    assertTrue(antPattern.acceptPath("source/ppp/foo.java"));
    assertTrue(antPattern.acceptPath("source/ppp/qqq/foo.java"));
    assertFalse(antPattern.acceptPath("src/ppp/qqq/foo.java"));

    assertTrue(antPattern.couldBeIncluded("ccc/fff/source/kkk"));
    assertTrue(antPattern.couldBeIncluded("ccc/fff/src/kkk"));
  }

  public void testIncludes() {
    final AntPattern antPattern = new AntPattern(true);
    antPattern.addIncludePattern("source/**/*.java");
    
    assertTrue(antPattern.acceptPath("source/foo.java"));
    assertTrue(antPattern.acceptPath("source/ppp/foo.java"));
    assertTrue(antPattern.acceptPath("source/ppp/qqq/foo.java"));
    assertFalse(antPattern.acceptPath("aa/source/ppp/qqq/foo.java"));
    assertFalse(antPattern.acceptPath("src/ppp/qqq/foo.java"));

    assertTrue(antPattern.couldBeIncluded("source"));
    assertTrue(antPattern.couldBeIncluded("source/aaa"));
    assertTrue(antPattern.couldBeIncluded("source/aaa/bbb"));
    assertTrue(antPattern.couldBeIncluded("source/fff/source/kkk"));
    assertFalse(antPattern.couldBeIncluded("bbb/source"));
    assertFalse(antPattern.couldBeIncluded("bbb/source/aaa"));
    assertFalse(antPattern.couldBeIncluded("ccc/fff/source/kkk"));
    assertFalse(antPattern.couldBeIncluded("ccc/fff/src/kkk"));
  }
  
  public void testIncludesAll() {
    final AntPattern antPattern = new AntPattern(true);
    
    assertTrue(antPattern.acceptPath("source1/foo.java"));
    assertTrue(antPattern.acceptPath("source/ppp/foo.class"));
    assertTrue(antPattern.acceptPath("source/ppp/qqq/bar.properties"));
    assertTrue(antPattern.acceptPath("src55/ppp/qqq/foo.bar"));

    assertTrue(antPattern.couldBeIncluded("ccc/fff/source/kkk"));
    assertTrue(antPattern.couldBeIncluded("ccc/fff/src/kkk"));
    assertTrue(antPattern.couldBeIncluded("ccc"));
  }

  public void testCouldBeExcluded() {
    final AntPattern antPattern = new AntPattern(true);
    antPattern.addIncludePattern("aaa/bbb/**");
    
    assertTrue(antPattern.couldBeIncluded("aaa"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb/source"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb/source/com"));
    assertFalse(antPattern.couldBeIncluded("aaa/bb/source/com"));
  }

  public void testCouldBeExcludedWithTrailingSlash() {
    final AntPattern antPattern = new AntPattern(true);
    antPattern.addIncludePattern("aaa/bbb/");

    assertFalse(antPattern.acceptPath("aaa/foo.java"));
    assertTrue(antPattern.acceptPath("aaa/bbb/bar.properties"));
    assertTrue(antPattern.acceptPath("aaa/bbb/source/a.xml"));
    assertTrue(antPattern.acceptPath("aaa/bbb/source/com/f.ttt"));
    assertFalse(antPattern.acceptPath("aaa/bb/source/com/m.bbb"));
    
    
    assertTrue(antPattern.couldBeIncluded("aaa"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb/source"));
    assertTrue(antPattern.couldBeIncluded("aaa/bbb/source/com"));
    assertFalse(antPattern.couldBeIncluded("aaa/bb/source/com"));
  }
  
}
