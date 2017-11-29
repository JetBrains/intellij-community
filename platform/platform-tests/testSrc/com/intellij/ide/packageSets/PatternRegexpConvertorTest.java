/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.packageSets;

import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
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