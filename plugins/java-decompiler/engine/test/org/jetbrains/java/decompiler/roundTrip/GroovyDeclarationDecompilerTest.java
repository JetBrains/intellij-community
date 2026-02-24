// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip;

import org.jetbrains.java.decompiler.roundTrip.fixtures.groovy.GroovyDecompilerRoundTripTestCase;
import org.junit.jupiter.api.Test;

public class GroovyDeclarationDecompilerTest extends GroovyDecompilerRoundTripTestCase {
  @Override
  protected String testCaseDir() {
    return "groovy/declaration";
  }

  @Test
  public void testGroovyClass() {
    doTest("pkg/GroovyClass");
  }

  @Test
  public void testGroovyTrait() {
    doTest("pkg/GroovyTrait");
  }
}
