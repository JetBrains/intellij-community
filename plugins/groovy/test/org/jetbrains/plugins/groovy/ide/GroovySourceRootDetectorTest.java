/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ide;

import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.GroovySourceRootDetector;

public class GroovySourceRootDetectorTest extends TestCase {
  public void testEmpty() {
    doTest("", "");
  }

  public void testComment() {
    doTest("//comment", "");
    doTest("  /* comment */", "");
  }

  public void testStatement() {
    doTest("def a = 0;", "");
  }

  public void testSimplePackage() {
    doTest("package simple", "simple");
    doTest("package simple;", "simple");
    doTest("""
             //comment
                         package simple;""", "simple");
  }

  public void testComplexPackage() {
    doTest("package com.simple", "com.simple");
    doTest("package com.simple;", "com.simple");
    doTest("""
             
                         package com.simple;\
             """, "com.simple");
    doTest("""
             /**
                           @author nik;
                         */
                         package com.simple;""", "com.simple");
  }

  public void doTest(String text, String expectedPackageName) {
    TestCase.assertEquals(expectedPackageName, GroovySourceRootDetector.getPackageName(text));
  }
}
