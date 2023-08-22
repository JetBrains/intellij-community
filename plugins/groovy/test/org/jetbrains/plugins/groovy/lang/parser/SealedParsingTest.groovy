// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

class SealedParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "types/sealed"
  }

  void testBasicNonsealed() { doTest() }

  void testBasicSealed() { doTest() }

  void testEnum() { doTest() }

  void testExplicitSubclasses() { doTest() }

  void testInterface() { doTest() }

  void testPermitsAfterExtends() { doTest() }

  void testPermitsAfterImplements() { doTest() }

  void testPermitsBeforeExtends() { doTest() }

  void testPermitsBeforeImplements() { doTest() }

  void testTrait() { doTest() }
}