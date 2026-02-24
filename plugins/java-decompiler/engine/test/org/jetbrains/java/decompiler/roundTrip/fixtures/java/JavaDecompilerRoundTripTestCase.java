// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures.java;

import org.jetbrains.java.decompiler.roundTrip.fixtures.Compiler;
import org.jetbrains.java.decompiler.roundTrip.fixtures.DecompilerRoundTripTestCase;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ArgumentsSource;

@ParameterizedClass
@ArgumentsSource(JavaCompilerArgumentsProvider.class)
public abstract class JavaDecompilerRoundTripTestCase extends DecompilerRoundTripTestCase {
  @Parameter
  protected Compiler compiler;

  /**
   * @see DecompilerRoundTripTestCase#doTest(Compiler, String, String...)
   */
  protected void doTest(String sourceFile, String... companionFileSystemItems) {
    doTest(compiler, sourceFile, companionFileSystemItems);
  }
}
