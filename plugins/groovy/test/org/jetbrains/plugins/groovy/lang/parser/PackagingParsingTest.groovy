// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class PackagingParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "packaging"
  }

  void testPack1() throws Throwable { doTest() }

  void testPack2() throws Throwable { doTest() }

  void testPack3() throws Throwable { doTest() }

  void testPack4() throws Throwable { doTest() }

  void testPack5() throws Throwable { doTest() }

  void testPack6() throws Throwable { doTest() }

  void testPack7() throws Throwable { doTest() }

  void testPack8() throws Throwable { doTest() }

  void testPack9() { doTest() }
}