// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.intellilang.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class PatternInstrumenter extends ClassVisitor implements Opcodes {
  static final String PATTERN_CACHE_NAME = "$_PATTERN_CACHE_$";
  static final String ASSERTIONS_DISABLED_NAME = "$assertionsDisabled";
  static final String JAVA_LANG_STRING = "Ljava/lang/String;";
  static final String JAVA_UTIL_REGEX_PATTERN = "[Ljava/util/regex/Pattern;";
  static final String NULL_PATTERN = "((((";

  private final String myPatternAnnotationClassName;
  private final boolean myDoAssert;
  private final InstrumentationClassFinder myClassFinder;
  private final Map<String, String> myAnnotationPatterns = new HashMap<>();
  private final LinkedHashSet<String> myPatterns = new LinkedHashSet<>();

  private String myClassName;
  private boolean myEnum;
  private boolean myInner;
  private boolean myHasAssertions;
  private boolean myHasStaticInitializer;
  private boolean myInstrumented;
  private RuntimeException myPostponedError;

  PatternInstrumenter(@NotNull String patternAnnotationClassName,
                      ClassVisitor classvisitor,
                      InstrumentationType instrumentation,
                      InstrumentationClassFinder classFinder) {
    super(Opcodes.API_VERSION, classvisitor);
    myPatternAnnotationClassName = patternAnnotationClassName;
    myDoAssert = instrumentation == InstrumentationType.ASSERT;
    myClassFinder = classFinder;
    myAnnotationPatterns.put(patternAnnotationClassName, NULL_PATTERN);
  }

  boolean instrumented() {
    return myInstrumented;
  }

  void markInstrumented() {
    myInstrumented = true;
    if (myPostponedError != null) {
      throw myPostponedError;
    }
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    myClassName = name;
    myEnum = (access & ACC_ENUM) != 0;
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (myClassName.equals(name)) {
      myInner = (access & ACC_STATIC) == 0;
    }
  }

  @Override
  public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    if (name.equals(ASSERTIONS_DISABLED_NAME)) {
      myHasAssertions = true;
    }
    return super.visitField(access, name, desc, signature, value);
  }

  @Override
  public void visitEnd() {
    if (myInstrumented) {
      for (String pattern : myPatterns) {
        // checks patterns so we can rely on them being valid at runtime
        try { Pattern.compile(pattern); }
        catch (Exception e) {
          throw new InstrumentationException("Illegal Pattern: " + pattern, e);
        }
      }

      addField(PATTERN_CACHE_NAME, ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC, JAVA_UTIL_REGEX_PATTERN);

      if (myDoAssert && !myHasAssertions) {
        addField(ASSERTIONS_DISABLED_NAME, ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC, "Z");
      }

      if (!myHasStaticInitializer) {
        MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        patchStaticInitializer(mv);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }
    }

    super.visitEnd();
  }

  private void addField(String name, int access, String desc) {
    cv.visitField(access, name, desc, null, null).visitEnd();
  }

  private void patchStaticInitializer(MethodVisitor mv) {
    if (myDoAssert && !myHasAssertions) {
      mv.visitLdcInsn(Type.getType("L" + myClassName + ";"));
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      Label l0 = new Label();
      mv.visitJumpInsn(IFNE, l0);
      mv.visitInsn(ICONST_1);
      Label l1 = new Label();
      mv.visitJumpInsn(GOTO, l1);
      mv.visitLabel(l0);
      mv.visitInsn(ICONST_0);
      mv.visitLabel(l1);
      mv.visitFieldInsn(PUTSTATIC, myClassName, ASSERTIONS_DISABLED_NAME, "Z");
    }

    mv.visitIntInsn(BIPUSH, myPatterns.size());
    mv.visitTypeInsn(ANEWARRAY, "java/util/regex/Pattern");
    mv.visitFieldInsn(PUTSTATIC, myClassName, PATTERN_CACHE_NAME, JAVA_UTIL_REGEX_PATTERN);

    int i = 0;
    for (String pattern : myPatterns) {
      mv.visitFieldInsn(GETSTATIC, myClassName, PATTERN_CACHE_NAME, JAVA_UTIL_REGEX_PATTERN);
      mv.visitIntInsn(BIPUSH, i++);
      mv.visitLdcInsn(pattern);
      mv.visitMethodInsn(INVOKESTATIC, "java/util/regex/Pattern", "compile", "(Ljava/lang/String;)Ljava/util/regex/Pattern;", false);
      mv.visitInsn(AASTORE);
    }
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    MethodVisitor methodvisitor = cv.visitMethod(access, name, desc, signature, exceptions);
    boolean isStatic = (access & ACC_STATIC) != 0;

    if (isStatic && name.equals("<clinit>")) {
      myHasStaticInitializer = true;
      return new ErrorPostponingMethodVisitor(this, name, methodvisitor) {
        @Override
        public void visitCode() {
          super.visitCode();
          patchStaticInitializer(mv);
        }
      };
    }

    if ((access & Opcodes.ACC_BRIDGE) == 0) {
      Type[] argTypes = Type.getArgumentTypes(desc);
      Type returnType = Type.getReturnType(desc);
      if (isCandidate(argTypes, returnType)) {
        int offset = !"<init>".equals(name) ? 0 : myEnum ? 2 : myInner ? 1 : 0;
        return new InstrumentationAdapter(this, methodvisitor, argTypes, returnType, myClassName, name, myDoAssert, isStatic, offset);
      }
    }

    return new ErrorPostponingMethodVisitor(this, name, methodvisitor);
  }

  private static boolean isCandidate(Type[] argTypes, Type returnType) {
    if (isStringType(returnType)) {
      return true;
    }
    for (Type argType : argTypes) {
      if (isStringType(argType)) {
        return true;
      }
    }
    return false;
  }

  static boolean isStringType(Type type) {
    return type.getSort() == Type.OBJECT && type.getDescriptor().equals(JAVA_LANG_STRING);
  }

  int addPattern(String s) {
    return myPatterns.add(s) ? myPatterns.size() - 1 : Arrays.asList(myPatterns.toArray()).indexOf(s);
  }

  /**
   * Returns a pattern string for meta-annotations, a {@link #NULL_PATTERN} for the pattern annotation,
   * or {@code null} for unrelated annotations.
   */
  @Nullable String getAnnotationPattern(String annotationClassName) {
    if (!myAnnotationPatterns.containsKey(annotationClassName)) {
      myAnnotationPatterns.put(annotationClassName, null);

      try (InputStream is = myClassFinder.getClassBytesAsStream(annotationClassName)) {
        if (is != null) {
          new ClassReader(is).accept(new ClassVisitor(Opcodes.API_VERSION) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
              boolean matches = myPatternAnnotationClassName.equals(Type.getType(desc).getClassName());
              return !matches ? null : new AnnotationVisitor(Opcodes.API_VERSION) {
                @Override
                public void visit(String name, Object value) {
                  if ("value".equals(name) && value instanceof String) {
                    myAnnotationPatterns.put(annotationClassName, (String)value);
                  }
                }
              };
            }
          }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
      }
      catch (IOException e) {
        Logger.getInstance(PatternInstrumenter.class).info("failed to read " + annotationClassName, e);
      }
    }

    return myAnnotationPatterns.get(annotationClassName);
  }

  void registerError(String methodName, @SuppressWarnings("SameParameterValue") String op, Throwable e) {
    if (myPostponedError == null) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      e.printStackTrace(new PrintStream(out));
      String message = "Operation '" + op + "' failed for " + myClassName + '.' + methodName + "(): " + e.getMessage() + '\n' + out;
      myPostponedError = new RuntimeException(message, e);
    }
    if (myInstrumented) {
      throw myPostponedError;
    }
  }
}