// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.util.Set;

class TMHAssertionGenerator {

  private static final String THREAD_ASSERTIONS_CLASS_NAME = "com/intellij/util/concurrency/ThreadingAssertions";
  private static final String GENERATE_ASSERTION_PARAMETER = "generateAssertion";

  private final String myThreadAssertionsClassName;
  private final Type myAnnotationClass;
  private final String myAssertionMethodName;

  TMHAssertionGenerator(String threadAssertionsClassName, Type annotationClass, String assertionMethodName) {
    myThreadAssertionsClassName = threadAssertionsClassName;
    myAnnotationClass = annotationClass;
    myAssertionMethodName = assertionMethodName;
  }

  boolean isMyAnnotation(String annotationDescriptor) {
    return myAnnotationClass.getDescriptor().equals(annotationDescriptor);
  }

  public AnnotationChecker getAnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
    return new AnnotationChecker(api, onShouldGenerateAssertion);
  }

  void generateAssertion(MethodVisitor writer, int methodStartLineNumber) {
    if (methodStartLineNumber != -1) {
      Label generatedCodeStart = new Label();
      writer.visitLabel(generatedCodeStart);
      writer.visitLineNumber(methodStartLineNumber, generatedCodeStart);
    }
    writer.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      myThreadAssertionsClassName,
      myAssertionMethodName,
      "()V",
      false
    );
  }

  static class AnnotationChecker extends AnnotationVisitor {
    private boolean myShouldGenerateAssertion = true;
    private final Runnable myOnShouldGenerateAssertion;

    private AnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
      super(api);
      myOnShouldGenerateAssertion = onShouldGenerateAssertion;
    }

    @Override
    public void visit(String annotationParameterName, Object value) {
      if (GENERATE_ASSERTION_PARAMETER.equals(annotationParameterName) && Boolean.FALSE.equals(value)) {
        myShouldGenerateAssertion = false;
      }
    }

    @Override
    public void visitEnd() {
      if (myShouldGenerateAssertion) {
        myOnShouldGenerateAssertion.run();
      }
    }
  }

  // TODO avoid hardcoding annotation names
  static @NotNull Set<? extends TMHAssertionGenerator> generators() {
    return GENERATORS;
  }

  private static final Set<? extends TMHAssertionGenerator> GENERATORS = generators(
    THREAD_ASSERTIONS_CLASS_NAME,
    "com/intellij/util/concurrency/annotations"
  );

  static @NotNull Set<? extends TMHAssertionGenerator> generators(
    @NotNull String threadAssertionsClassName,
    @NotNull String packageString
  ) {
    return Set.of(
      generator(threadAssertionsClassName, packageString + "/RequiresEdt", "assertEventDispatchThread"),
      generator(threadAssertionsClassName, packageString + "/RequiresBackgroundThread", "assertBackgroundThread"),
      generator(threadAssertionsClassName, packageString + "/RequiresReadLock", "softAssertReadAccess"),
      generator(threadAssertionsClassName, packageString + "/RequiresReadLockAbsence", "assertNoReadAccess"),
      generator(threadAssertionsClassName, packageString + "/RequiresWriteLock", "assertWriteAccess")
    );
  }

  private static @NotNull TMHAssertionGenerator generator(
    @NotNull String threadAssertionsClassName,
    @NotNull String annotationClassName,
    @NotNull String assertionMethodName
  ) {
    return new TMHAssertionGenerator(threadAssertionsClassName, Type.getType("L" + annotationClassName + ";"), assertionMethodName);
  }
}
