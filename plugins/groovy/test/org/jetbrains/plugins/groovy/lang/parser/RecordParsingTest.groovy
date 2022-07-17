// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

class RecordParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "types/record"
  }

  void testRecord1() { doTest() }

  void testRecord2() { doTest() }

  void testRecord3() { doTest() }

  void testRecord4() { doTest() }

  void testRecord5() { doTest() }

  void testRecord6() { doTest() }

  void testRecord7() { doTest() }

  void testRecord8() { doTest() }
}
