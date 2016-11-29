/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.intellilang.instrumentation;

import com.intellij.compiler.instrumentation.FailSafeMethodVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

class InstrumentationAdapter extends FailSafeMethodVisitor implements Opcodes {
  @NonNls
  private static final String RETURN_VALUE_NAME = "$returnvalue$";

  private final Type[] myArgTypes;
  private final Type myReturnType;
  private final int myAccess;
  private final String myMethodName;

  private final PatternInstrumenter myInstrumenter;

  private final List<PatternValue> myParameterPatterns = new ArrayList<PatternValue>();
  private PatternValue myMethodPattern;

  private Label myAssertLabel;

  public InstrumentationAdapter(PatternInstrumenter instrumenter,
                                MethodVisitor methodvisitor,
                                Type[] argTypes,
                                Type returnType,
                                int access,
                                String name) {
    super(Opcodes.API_VERSION, methodvisitor);
    myInstrumenter = instrumenter;
    myArgTypes = argTypes;
    myReturnType = returnType;
    myAccess = access;
    myMethodName = name;
  }

  public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
    final AnnotationVisitor annotationvisitor = mv.visitParameterAnnotation(parameter, desc, visible);

    if (myArgTypes[parameter].getSort() == Type.OBJECT) {
      final String annotationClassName = Type.getType(desc).getClassName();
      if (myInstrumenter.acceptAnnotation(annotationClassName)) {
        @Nullable final String patternString = myInstrumenter.getAnnotationPattern(annotationClassName);
        final String[] strings = annotationClassName.split("\\.");
        final PatternValue patternValue = new PatternValue(parameter, strings[strings.length - 1], patternString);
        myParameterPatterns.add(patternValue);

        // dig into the annotation and get the "value" element if pattern isn't present yet
        return patternString == null ? new MyAnnotationVisitor(annotationvisitor, patternValue) : annotationvisitor;
      }
    }
    return annotationvisitor;
  }

  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    final AnnotationVisitor annotationvisitor = mv.visitAnnotation(desc, visible);

    if (myReturnType.getSort() == Type.OBJECT) {
      final String annotationClassName = Type.getType(desc).getClassName();
      if (myInstrumenter.acceptAnnotation(annotationClassName)) {
        @Nullable final String pattern = myInstrumenter.getAnnotationPattern(annotationClassName);
        final String[] strings = annotationClassName.split("\\.");
        myMethodPattern = new PatternValue(-1, strings[strings.length - 1], pattern);

        return pattern == null ? new MyAnnotationVisitor(annotationvisitor, myMethodPattern) : annotationvisitor;
      }
    }
    return annotationvisitor;
  }

  public void visitCode() {
    for (PatternValue parameter : myParameterPatterns) {
      int j;
      if ((myAccess & Opcodes.ACC_STATIC) == 0) {
        // special case: ctor of non-static inner classes (see IDEA-10889)
        if ("<init>".equals(myMethodName)) {
          // ACC_INTERFACE is (ab-)used to tunnel the information about the non-static inner class
          j = (myInstrumenter.myIsNonStaticInnerClass) ? 1 + myArgTypes[0].getSize() // skip first (synthetic) "Outer.this" parameter
              : 1;
        }
        else {
          j = 1;
        }
      }
      else {
        j = 0;
      }

      for (int l = 0; l < parameter.index; l++) {
        j += myArgTypes[l].getSize();
      }

      final Label checked = new Label();

      addPatternTest(parameter.patternIndex, checked, j);

      addPatternAssertion(MessageFormat.format("Argument {0} for @{1} parameter of {2}.{3} does not match pattern {4}", parameter.index,
                                               parameter.annotation, myInstrumenter.myClassName, myMethodName, parameter.pattern), false);

      mv.visitLabel(checked);
    }

    if (myMethodPattern != null) {
      myAssertLabel = new Label();
    }
  }

  public void visitInsn(int opcode) {
    if (opcode == Opcodes.ARETURN && myAssertLabel != null) {
      mv.visitJumpInsn(Opcodes.GOTO, myAssertLabel);
    }
    else {
      mv.visitInsn(opcode);
    }
  }

  public void visitMaxs(int maxStack, int maxLocals) {
    try {
      if (myAssertLabel != null) {

        // next index for synthetic variable that holds return value
        final int var = maxLocals + 1;

        mv.visitLabel(myAssertLabel);

        mv.visitVarInsn(Opcodes.ASTORE, var);

        final Label end = new Label();
        addPatternTest(myMethodPattern.patternIndex, end, var);

        addPatternAssertion(MessageFormat.format("Return value of method {0}.{1} annotated as @{2} does not match pattern {3}",
                                                 myInstrumenter.myClassName, myMethodName, myMethodPattern.annotation,
                                                 myMethodPattern.pattern), true);

        mv.visitLabel(end);
        mv.visitLocalVariable(RETURN_VALUE_NAME, PatternInstrumenter.JAVA_LANG_STRING, null, myAssertLabel, end, var);

        mv.visitVarInsn(Opcodes.ALOAD, var);
        mv.visitInsn(Opcodes.ARETURN);
      }

      super.visitMaxs(maxStack, maxLocals);
    }
    catch (Throwable e) {
      myInstrumenter.registerError(myMethodName, "visitMaxs", e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void addPatternTest(int patternIndex, Label label, int varIndex) {
    if (myInstrumenter.myInstrumentationType == InstrumentationType.ASSERT) {
      mv.visitFieldInsn(Opcodes.GETSTATIC, myInstrumenter.myClassName, PatternInstrumenter.ASSERTIONS_DISABLED_NAME, "Z");
      mv.visitJumpInsn(Opcodes.IFNE, label);
    }

    mv.visitVarInsn(Opcodes.ALOAD, varIndex);
    mv.visitJumpInsn(Opcodes.IFNULL, label);

    mv.visitFieldInsn(GETSTATIC, myInstrumenter.myClassName, PatternInstrumenter.PATTERN_CACHE_NAME,
                      "[Ljava/util/regex/Pattern;");
    mv.visitIntInsn(BIPUSH, patternIndex);
    mv.visitInsn(AALOAD);
    mv.visitVarInsn(ALOAD, varIndex);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/regex/Pattern", "matcher", "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/regex/Matcher", "matches", "()Z", false);

    mv.visitJumpInsn(Opcodes.IFNE, label);
  }

  // TODO: add actual value to assertion message
  private void addPatternAssertion(String message, boolean isMethod) {
    if (myInstrumenter.myInstrumentationType == InstrumentationType.ASSERT) {
      addThrow("java/lang/AssertionError", "(Ljava/lang/Object;)V", message);
    }
    else if (myInstrumenter.myInstrumentationType == InstrumentationType.EXCEPTION) {
      if (isMethod) {
        addThrow("java/lang/IllegalStateException", "(Ljava/lang/String;)V", message);
      }
      else {
        addThrow("java/lang/IllegalArgumentException", "(Ljava/lang/String;)V", message);
      }
    }
    myInstrumenter.markInstrumented();
  }

  private void addThrow(@NonNls String throwableClass, @NonNls String ctorSignature, String message) {
    mv.visitTypeInsn(Opcodes.NEW, throwableClass);
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn(message);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, throwableClass, "<init>", ctorSignature, false);
    mv.visitInsn(Opcodes.ATHROW);
  }

  private static class MyAnnotationVisitor extends AnnotationVisitor {
    private final AnnotationVisitor av;
    private final PatternValue myPatternValue;

    public MyAnnotationVisitor(AnnotationVisitor annotationvisitor, PatternValue v) {
      super(Opcodes.API_VERSION);
      av = annotationvisitor;
      myPatternValue = v;
    }

    public void visit(@NonNls String name, Object value) {
      av.visit(name, value);
      if ("value".equals(name) && value instanceof String) {
        myPatternValue.set((String)value);
      }
    }

    public void visitEnum(String name, String desc, String value) {
      av.visitEnum(name, desc, value);
    }

    public AnnotationVisitor visitAnnotation(String name, String desc) {
      return av.visitAnnotation(name, desc);
    }

    public AnnotationVisitor visitArray(String name) {
      return av.visitArray(name);
    }

    public void visitEnd() {
      av.visitEnd();
    }
  }

  class PatternValue {
    final int index;
    final String annotation;
    String pattern;
    int patternIndex;

    PatternValue(int index, String annotation, String pattern) {
      this.index = index;
      this.annotation = annotation;
      if (pattern != null) set(pattern);
    }

    void set(String s) {
      assert pattern == null;
      patternIndex = myInstrumenter.addPattern(pattern = s);
    }
  }
}
