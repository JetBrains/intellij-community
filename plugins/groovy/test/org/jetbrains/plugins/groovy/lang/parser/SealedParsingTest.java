// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class SealedParsingTest extends GroovyParsingTestCase {
  @Override
  public String getBasePath() {
    return super.getBasePath() + "types/sealed";
  }

  public void testBasicNonsealed() { doTest(); }

  public void testBasicSealed() { doTest(); }

  public void testEnum() { doTest(); }

  public void testExplicitSubclasses() { doTest(); }

  public void testInterface() { doTest(); }

  public void testPermitsAfterExtends() { doTest(); }

  public void testPermitsAfterImplements() { doTest(); }

  public void testPermitsBeforeExtends() { doTest(); }

  public void testPermitsBeforeImplements() { doTest(); }

  public void testTrait() { doTest(); }
}
