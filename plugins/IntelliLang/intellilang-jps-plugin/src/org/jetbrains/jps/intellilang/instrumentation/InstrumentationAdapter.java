// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.instrumentation;

import com.intellij.compiler.instrumentation.FailSafeMethodVisitor;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.org.objectweb.asm.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.jps.intellilang.instrumentation.PatternInstrumenter.isStringType;

class InstrumentationAdapter extends FailSafeMethodVisitor implements Opcodes {
  @SuppressWarnings("SpellCheckingInspection") private static final String RETURN_VALUE_NAME = "$returnvalue$";

  private final PatternInstrumenter myInstrumenter;
  private final Type[] myArgTypes;
  private final Type myReturnType;
  private final String myClassName;
  private final String myMethodName;
  private final boolean myDoAssert;
  private final boolean myIsStatic;
  private int myParamAnnotationOffset;

  private final List<PatternValue> myParameterPatterns = new ArrayList<>();
  private PatternValue myMethodPattern;
  private Label myCheckReturnLabel;

  InstrumentationAdapter(PatternInstrumenter instrumenter,
                         MethodVisitor mv,
                         Type[] argTypes,
                         Type returnType,
                         String className,
                         String methodName,
                         boolean doAssert,
                         boolean isStatic,
                         int paramAnnotationOffset) {
    super(Opcodes.API_VERSION, mv);
    myInstrumenter = instrumenter;
    myDoAssert = doAssert;
    myArgTypes = argTypes;
    myReturnType = returnType;
    myClassName = className;
    myMethodName = methodName;
    myIsStatic = isStatic;
    myParamAnnotationOffset = paramAnnotationOffset;
  }

  @Override
  public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
    if (myParamAnnotationOffset != 0 && parameterCount == myArgTypes.length) {
      myParamAnnotationOffset = 0;
    }
    super.visitAnnotableParameterCount(parameterCount, visible);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
    AnnotationVisitor av = mv.visitParameterAnnotation(parameter, desc, visible);

    if (isStringType(myArgTypes[parameter + myParamAnnotationOffset])) {
      String annotationClassName = Type.getType(desc).getClassName();
      String pattern = myInstrumenter.getAnnotationPattern(annotationClassName);
      if (pattern != null) {
        String shortName = annotationClassName.substring(annotationClassName.lastIndexOf('.') + 1);
        PatternValue patternValue = new PatternValue(parameter + myParamAnnotationOffset, shortName, pattern);
        myParameterPatterns.add(patternValue);
        if (Strings.areSameInstance(pattern, PatternInstrumenter.NULL_PATTERN)) {
          return new MyAnnotationVisitor(av, patternValue);
        }
      }
    }

    return av;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    AnnotationVisitor av = mv.visitAnnotation(desc, visible);

    if (isStringType(myReturnType)) {
      String annotationClassName = Type.getType(desc).getClassName();
      String pattern = myInstrumenter.getAnnotationPattern(annotationClassName);
      if (pattern != null) {
        String shortName = annotationClassName.substring(annotationClassName.lastIndexOf('.') + 1);
        myMethodPattern = new PatternValue(-1, shortName, pattern);
        if (Strings.areSameInstance(pattern, PatternInstrumenter.NULL_PATTERN)) {
          return new MyAnnotationVisitor(av, myMethodPattern);
        }
      }
    }

    return av;
  }

  @Override
  public void visitCode() {
    for (PatternValue parameter : myParameterPatterns) {
      int var = myIsStatic ? 0 : 1;
      for (int l = 0; l < parameter.parameterIndex; l++) var += myArgTypes[l].getSize();

      Label checked = new Label();
      addPatternTest(parameter.patternIndex, checked, var);
      String message = MessageFormat.format("Argument {0} for @{1} parameter of {2}.{3} does not match pattern {4}",
                                            parameter.parameterIndex, parameter.annotation, myClassName, myMethodName, parameter.pattern);
      addPatternAssertion(message, false);
      mv.visitLabel(checked);
    }

    if (myMethodPattern != null) {
      myCheckReturnLabel = new Label();
    }
  }

  @Override
  public void visitInsn(int opcode) {
    if (opcode == Opcodes.ARETURN && myCheckReturnLabel != null) {
      mv.visitJumpInsn(Opcodes.GOTO, myCheckReturnLabel);
    }
    else {
      mv.visitInsn(opcode);
    }
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    try {
      if (myCheckReturnLabel != null) {
        int var = maxLocals + 1; // next index for synthetic variable that holds return value
        mv.visitLabel(myCheckReturnLabel);
        mv.visitVarInsn(Opcodes.ASTORE, var);

        Label checked = new Label();
        addPatternTest(myMethodPattern.patternIndex, checked, var);
        String message = MessageFormat.format("Return value of method {0}.{1} annotated as @{2} does not match pattern {3}",
                                              myClassName, myMethodName, myMethodPattern.annotation, myMethodPattern.pattern);
        addPatternAssertion(message, true);
        mv.visitLabel(checked);
        mv.visitLocalVariable(RETURN_VALUE_NAME, PatternInstrumenter.JAVA_LANG_STRING, null, myCheckReturnLabel, checked, var);
        mv.visitVarInsn(Opcodes.ALOAD, var);
        mv.visitInsn(Opcodes.ARETURN);
      }

      super.visitMaxs(maxStack, maxLocals);
    }
    catch (Throwable e) {
      myInstrumenter.registerError(myMethodName, "visitMaxs", e);
    }
  }

  private void addPatternTest(int patternIndex, Label label, int varIndex) {
    if (myDoAssert) {
      mv.visitFieldInsn(Opcodes.GETSTATIC, myClassName, PatternInstrumenter.ASSERTIONS_DISABLED_NAME, "Z");
      mv.visitJumpInsn(Opcodes.IFNE, label);
    }

    mv.visitVarInsn(Opcodes.ALOAD, varIndex);
    mv.visitJumpInsn(Opcodes.IFNULL, label);

    mv.visitFieldInsn(GETSTATIC, myClassName, PatternInstrumenter.PATTERN_CACHE_NAME, "[Ljava/util/regex/Pattern;");
    mv.visitIntInsn(BIPUSH, patternIndex);
    mv.visitInsn(AALOAD);
    mv.visitVarInsn(ALOAD, varIndex);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/regex/Pattern", "matcher", "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/regex/Matcher", "matches", "()Z", false);

    mv.visitJumpInsn(Opcodes.IFNE, label);
  }

  // TODO: add actual value to assertion message
  private void addPatternAssertion(String message, boolean isMethod) {
    if (myDoAssert) {
      addThrow("java/lang/AssertionError", "(Ljava/lang/Object;)V", message);
    }
    else if (isMethod) {
      addThrow("java/lang/IllegalStateException", "(Ljava/lang/String;)V", message);
    }
    else {
      addThrow("java/lang/IllegalArgumentException", "(Ljava/lang/String;)V", message);
    }
    myInstrumenter.markInstrumented();
  }

  private void addThrow(String throwableClass, String ctorSignature, String message) {
    mv.visitTypeInsn(Opcodes.NEW, throwableClass);
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn(message);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, throwableClass, "<init>", ctorSignature, false);
    mv.visitInsn(Opcodes.ATHROW);
  }

  private static class MyAnnotationVisitor extends AnnotationVisitor {
    private final PatternValue myPatternValue;

    MyAnnotationVisitor(AnnotationVisitor annotationvisitor, PatternValue v) {
      super(Opcodes.API_VERSION, annotationvisitor);
      myPatternValue = v;
    }

    @Override
    public void visit(String name, Object value) {
      av.visit(name, value);
      if ("value".equals(name) && value instanceof String) {
        myPatternValue.set((String)value);
      }
    }
  }

  private class PatternValue {
    final int parameterIndex;
    final String annotation;
    String pattern = null;
    int patternIndex = -1;

    PatternValue(int parameterIndex, String annotation, String pattern) {
      this.parameterIndex = parameterIndex;
      this.annotation = annotation;
      if (!Strings.areSameInstance(pattern, PatternInstrumenter.NULL_PATTERN)) {
        set(pattern);
      }
    }

    void set(String s) {
      assert pattern == null;
      patternIndex = myInstrumenter.addPattern(pattern = s);
    }
  }
}