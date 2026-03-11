// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures.groovy;

import org.jetbrains.java.decompiler.roundTrip.fixtures.Compiler;
import org.jetbrains.java.decompiler.roundTrip.fixtures.DecompilerRoundTripTestCase;

import java.util.List;

public abstract class GroovyDecompilerRoundTripTestCase extends DecompilerRoundTripTestCase {
  /**
   * @see DecompilerRoundTripTestCase#doTest(Compiler, String, List, String...)
   */
  protected void doTest(String sourceFile, String... companionFileSystemItems) {
    doTest(sourceFile, List.of(), companionFileSystemItems);
  }

  /**
   * @see DecompilerRoundTripTestCase#doTest(Compiler, String, List, String...)
   */
  protected void doTest(String sourceFile, List<String> compilerOptions, String... companionFileSystemItems) {
    doTest(Compiler.GROOVYC, sourceFile, compilerOptions, companionFileSystemItems);
  }
}
