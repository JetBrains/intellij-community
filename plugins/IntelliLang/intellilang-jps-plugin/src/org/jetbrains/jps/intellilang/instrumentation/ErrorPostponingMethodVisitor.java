// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.instrumentation;

import com.intellij.compiler.instrumentation.FailSafeMethodVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

/**
 * @author Eugene Zhuravlev
 */
public class ErrorPostponingMethodVisitor extends FailSafeMethodVisitor {
  private final PatternInstrumenter myInstrumenter;
  private final String myMethodName;

  public ErrorPostponingMethodVisitor(@NotNull PatternInstrumenter instrumenter, String methodName, @Nullable MethodVisitor methodvisitor) {
    super(Opcodes.API_VERSION, methodvisitor);
    myInstrumenter = instrumenter;
    myMethodName = methodName;
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    try {
      super.visitMaxs(maxStack, maxLocals);
    }
    catch (Throwable e) {
      myInstrumenter.registerError(myMethodName, "visitMaxs", e);
    }
  }
}