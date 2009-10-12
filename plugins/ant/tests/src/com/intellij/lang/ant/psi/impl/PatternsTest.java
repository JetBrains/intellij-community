/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
