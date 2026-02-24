// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures.groovy;

import org.jetbrains.java.decompiler.roundTrip.fixtures.Compiler;
import org.jetbrains.java.decompiler.roundTrip.fixtures.DecompilerRoundTripTestCase;

public abstract class GroovyDecompilerRoundTripTestCase extends DecompilerRoundTripTestCase {
  /**
   * @see DecompilerRoundTripTestCase#doTest(Compiler, String, String...)
   */
  protected void doTest(String sourceFile, String... companionFileSystemItems) {
    doTest(Compiler.GROOVYC, sourceFile, companionFileSystemItems);
  }
}
