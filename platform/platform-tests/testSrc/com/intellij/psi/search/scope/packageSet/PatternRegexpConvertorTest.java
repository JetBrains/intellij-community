/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.psi.search.scope.packageSet;

import junit.framework.TestCase;

public class PatternRegexpConvertorTest extends TestCase {
   public void testConvertToRegexp() {
     assertEquals("a\\.[^\\.]*", FilePatternPackageSet.convertToRegexp("a.*", '.'));
     assertEquals("a\\.(.*\\.)?[^\\.]*", FilePatternPackageSet.convertToRegexp("a..*", '.'));
     assertEquals("a\\/[^\\/]*", FilePatternPackageSet.convertToRegexp("a/*", '/'));
     assertEquals("a\\/.*\\.css", FilePatternPackageSet.convertToRegexp("a/*.css", '/'));
     assertEquals("a\\/(.*\\/)?[^\\/]*", FilePatternPackageSet.convertToRegexp("a//*", '/'));
     assertEquals("[^\\.]*", FilePatternPackageSet.convertToRegexp("*", '.'));
  }
}