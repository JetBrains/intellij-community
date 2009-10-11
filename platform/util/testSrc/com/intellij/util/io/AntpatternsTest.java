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
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: May 2, 2007
 */
public class AntpatternsTest extends TestCase {
  
  public void testPatternConversion() {
    final Pattern testPattern = convertToPattern("**/test/?*.java");
    
    assertTrue(testPattern.matcher("C:/test/AAA.java").matches());
    assertTrue(testPattern.matcher("p1/p2/p3/test/B.java").matches());
    assertTrue(testPattern.matcher("test/AAA.java").matches());
    assertFalse(testPattern.matcher("test/.java").matches());
    assertFalse(testPattern.matcher("tes/AAA.java").matches());
    assertFalse(testPattern.matcher("test/subpackage/AAA.java").matches());
    
    final Pattern sourcesPattern = convertToPattern("**/sources\\");
    assertTrue(sourcesPattern.matcher("C:/sources/HHH.java").matches());
    assertTrue(sourcesPattern.matcher("sources/HHH.class").matches());
    assertTrue(sourcesPattern.matcher("p1/p2/p3/sources/subpackage/TTT.java").matches());
    assertTrue(sourcesPattern.matcher("p1/p2/p3/p4/p5/sources/subpackage/TTT.java").matches());
    assertFalse(sourcesPattern.matcher("p1/source/subpackage/TTT.java").matches());

    final Pattern asteriskPattern = convertToPattern("CVS/**/foo.bar");
    assertFalse(asteriskPattern.matcher("CVS/entries/aaafoo.bar").matches());

    final Pattern asteriskPattern1 = convertToPattern("CVS/**/ttt/");
    assertFalse(asteriskPattern1.matcher("CVS/Attt/foo.bar").matches());
    
    final Pattern cvsPattern = convertToPattern("**/CVS/*");
    assertTrue(cvsPattern.matcher("CVS/Repository").matches());
    assertTrue(cvsPattern.matcher("org/apache/CVS/Entries").matches());
    assertTrue(cvsPattern.matcher("org/apache/jakarta/tools/ant/CVS/Entries").matches());
    assertFalse(cvsPattern.matcher("org/apache/jakarta/tools/ant/CVS/Entries/aaa").matches());
    assertFalse(cvsPattern.matcher("org/apache/CVS/foo/bar/Entries").matches());
    
    final Pattern jakartaPattern = convertToPattern("org/apache/jakarta/**");
    assertTrue(jakartaPattern.matcher("org/apache/jakarta/tools/ant/docs/index.html").matches());
    assertTrue(jakartaPattern.matcher("org/apache/jakarta/test.xml").matches());
    assertTrue(jakartaPattern.matcher("org/apache/jakarta").matches());
    assertFalse(jakartaPattern.matcher("org/apache/jakartaaaa").matches());
    assertFalse(jakartaPattern.matcher("org/apache/xyz.java").matches());
    
    final Pattern apacheCvsPattern = convertToPattern("org/apache/**/CVS/*");
    assertTrue(apacheCvsPattern.matcher("org/apache/CVS/Entries").matches());
    assertTrue(apacheCvsPattern.matcher("org/apache/jakarta/tools/ant/CVS/Entries").matches());
    assertFalse(apacheCvsPattern.matcher("org/apache/CVS/foo/bar/Entries").matches());

    final Pattern pattern = convertToPattern("/aaa.txt");
    assertFalse(pattern.matcher("/aaa.txt").matches());
    assertTrue(pattern.matcher("aaa.txt").matches());

    final Pattern samplePattern = convertToPattern("dir/subdi*/sample.txt");
    assertTrue(samplePattern.matcher("dir/subdir/sample.txt").matches());

    final Pattern samplePattern2 = convertToPattern("dir/subdi*/");
    assertTrue(samplePattern2.matcher("dir/subdir/sample.txt").matches());
    assertTrue(samplePattern2.matcher("dir/subdir/foo.txt").matches());
    assertTrue(samplePattern2.matcher("dir/subdir/aaa/foo.txt").matches());
  }

  public void testDoubleAsterisk() {
    final Pattern pattern = convertToPattern("dir/s**");
    assertTrue(pattern.matcher("dir/subdir").matches());
    assertFalse(pattern.matcher("dir/subdir/sample.txt").matches());
  }

  public void testDoubleAsteriskInside() {
    final Pattern pattern = convertToPattern("dir/s**/ttt");
    assertTrue(pattern.matcher("dir/subdir/ttt").matches());
    assertFalse(pattern.matcher("dir/subdir/aaa/ttt").matches());
    assertFalse(pattern.matcher("dir/subdir").matches());
  }

  public void testDoubleAsteriskOnly() {
    final Pattern pattern = convertToPattern("**");
    assertTrue(pattern.matcher("dir/subdir/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir/aaa/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir").matches());
  }

  public void testDoubleAsteriskOnly2() {
    final Pattern pattern = convertToPattern("/**");
    assertTrue(pattern.matcher("dir/subdir/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir/aaa/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir").matches());
  }

  public void testAsterisks() {
    final Pattern pattern = convertToPattern("dir/*?*");
    assertTrue(pattern.matcher("dir/subdir").matches());
    assertFalse(pattern.matcher("dir/subdir/ttt").matches());
    assertFalse(pattern.matcher("dir/subdir/aaa/ttt.txt").matches());
  }

  public void testTrailingAsterisks() {
    final Pattern pattern = convertToPattern("dir/subdir/**");
    assertTrue(pattern.matcher("dir/subdir/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir/aaa/ttt.txt").matches());
    assertTrue(pattern.matcher("dir/subdir").matches());
  }

  public void testTrailingAsterisks2() {
    final Pattern pattern = convertToPattern("dir/subdir/");
    assertTrue(pattern.matcher("dir/subdir/ttt").matches());
    assertTrue(pattern.matcher("dir/subdir/aaa/ttt.txt").matches());
    assertTrue(pattern.matcher("dir/subdir").matches());
  }
  
  private Pattern convertToPattern(final String antPattern) {
    return Pattern.compile(FileUtil.convertAntToRegexp(antPattern));
  }
}
