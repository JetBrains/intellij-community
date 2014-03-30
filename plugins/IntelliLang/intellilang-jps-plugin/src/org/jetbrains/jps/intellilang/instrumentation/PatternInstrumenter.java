/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.*;
import org.jetbrains.jps.intellilang.model.InstrumentationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

class PatternInstrumenter extends ClassVisitor implements Opcodes {
  @NonNls static final String PATTERN_CACHE_NAME = "$_PATTERN_CACHE_$";
  @NonNls static final String ASSERTIONS_DISABLED_NAME = "$assertionsDisabled";
  @NonNls static final String JAVA_LANG_STRING = "Ljava/lang/String;";
  @NonNls static final String JAVA_UTIL_REGEX_PATTERN = "[Ljava/util/regex/Pattern;";

  private boolean myHasAssertions;
  private boolean myHasStaticInitializer;

  private final LinkedHashSet<String> myPatterns = new LinkedHashSet<String>();

  private final String myPatternAnnotationClassName;
  final InstrumentationType myInstrumentationType;
  private final InstrumentationClassFinder myClassFinder;
  private final Map<String, String> myAnnotationNameToPatternMap = new HashMap<String, String>(); // can contain null values!
  private final Set<String> myProcessedAnnotations = new HashSet<String>(); // checked annotation classes

  String myClassName;
  private boolean myInstrumented;
  private RuntimeException myPostponedError;
  boolean myIsNonStaticInnerClass;

  public PatternInstrumenter(@NotNull String patternAnnotationClassName, ClassVisitor classvisitor,
                             InstrumentationType instrumentation,
                             InstrumentationClassFinder classFinder) {
    super(Opcodes.ASM4, classvisitor);
    myPatternAnnotationClassName = patternAnnotationClassName;

    myInstrumentationType = instrumentation;
    myClassFinder = classFinder;
    // initial setup: null value means we should discover the pattern string 'inplace'
    myAnnotationNameToPatternMap.put(patternAnnotationClassName, null);
    myProcessedAnnotations.add(patternAnnotationClassName);
  }

  public boolean instrumented() {
    return myInstrumented;
  }

  void markInstrumented() {
    myInstrumented = true;
    processPostponedErrors();
  }

  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    myClassName = name;
  }

  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    super.visitInnerClass(name, outerName, innerName, access);
    if (myClassName.equals(name)) {
      myIsNonStaticInnerClass = (access & ACC_STATIC) == 0;
    }
  }

  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    if (name.equals(ASSERTIONS_DISABLED_NAME)) {
      myHasAssertions = true;
    }
    else if (name.equals(PATTERN_CACHE_NAME)) {
      throw new InstrumentationException("Error: Processing an already instrumented class: " + myClassName + ". Please recompile the affected class(es) or rebuild the project.");
    }

    return super.visitField(access, name, desc, signature, value);
  }

  public void visitEnd() {
    if (myInstrumented) {
      addField(PATTERN_CACHE_NAME, ACC_PRIVATE + ACC_FINAL + ACC_STATIC + ACC_SYNTHETIC, JAVA_UTIL_REGEX_PATTERN);

      if (myInstrumentationType == InstrumentationType.ASSERT) {
        if (!myHasAssertions) {
          addField(ASSERTIONS_DISABLED_NAME, ACC_FINAL + ACC_STATIC + ACC_SYNTHETIC, "Z");
        }
      }

      if (!myHasStaticInitializer) {
        createStaticInitializer();
      }
    }

    super.visitEnd();
  }

  private void addField(String name, int modifiers, String type) {
    final FieldVisitor fv = cv.visitField(modifiers, name, type, null, null);
    fv.visitEnd();
  }

  private void createStaticInitializer() {
    final MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();

    patchStaticInitializer(mv);

    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void patchStaticInitializer(MethodVisitor mv) {
    if (!myHasAssertions && myInstrumentationType == InstrumentationType.ASSERT) {
      initAssertions(mv);
    }

    initPatterns(mv);
  }

  // verify pattern and add compiled pattern to static cache
  private void initPatterns(MethodVisitor mv) {
    mv.visitIntInsn(BIPUSH, myPatterns.size());
    mv.visitTypeInsn(ANEWARRAY, "java/util/regex/Pattern");
    mv.visitFieldInsn(PUTSTATIC, myClassName, PATTERN_CACHE_NAME, JAVA_UTIL_REGEX_PATTERN);

    int i = 0;
    for (String pattern : myPatterns) {
      // check the pattern so we can rely on the pattern being valid at runtime
      try {
        Pattern.compile(pattern);
      }
      catch (Exception e) {
        throw new InstrumentationException("Illegal Pattern: " + pattern, e);
      }

      mv.visitFieldInsn(GETSTATIC, myClassName, PATTERN_CACHE_NAME, JAVA_UTIL_REGEX_PATTERN);
      mv.visitIntInsn(BIPUSH, i++);
      mv.visitLdcInsn(pattern);
      mv.visitMethodInsn(INVOKESTATIC, "java/util/regex/Pattern", "compile", "(Ljava/lang/String;)Ljava/util/regex/Pattern;");
      mv.visitInsn(AASTORE);
    }
  }

  // add assert startup code
  private void initAssertions(MethodVisitor mv) {
    mv.visitLdcInsn(Type.getType("L" + myClassName + ";"));
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z");
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

  public MethodVisitor visitMethod(final int access, final String name, String desc, String signature, String[] exceptions) {
    final MethodVisitor methodvisitor = cv.visitMethod(access, name, desc, signature, exceptions);

    // patch static initializer
    if ((access & ACC_STATIC) != 0 && name.equals("<clinit>")) {
      myHasStaticInitializer = true;

      return new ErrorPostponingMethodVisitor(this, name, methodvisitor) {
        public void visitCode() {
          super.visitCode();
          patchStaticInitializer(mv);
        }
      };
    }

    final Type[] argTypes = Type.getArgumentTypes(desc);
    final Type returnType = Type.getReturnType(desc);

    // don't dig through the whole method if there's nothing to do in it
    if (isStringType(returnType)) {
      return new InstrumentationAdapter(this, methodvisitor, argTypes, returnType, access, name);
    }
    else {
      for (Type type : argTypes) {
        if (isStringType(type)) {
          return new InstrumentationAdapter(this, methodvisitor, argTypes, returnType, access, name);
        }
      }
    }

    return new ErrorPostponingMethodVisitor(this, name, methodvisitor);
  }

  private static boolean isStringType(Type type) {
    return type.getSort() == Type.OBJECT && type.getDescriptor().equals(JAVA_LANG_STRING);
  }

  public int addPattern(String s) {
    if (myPatterns.add(s)) {
      return myPatterns.size() - 1;
    }
    return Arrays.asList(myPatterns.toArray()).indexOf(s);
  }

  public boolean acceptAnnotation(String annotationClassName) {
    if (annotationClassName == null) {
      // unfortunately sometimes ASM may return null values
      return false; 
    }
    processAnnotation(annotationClassName);
    return myAnnotationNameToPatternMap.containsKey(annotationClassName);
  }

  /**
   * @param annotationClassname
   * @return pattern string for 'alias' annotations, as specified in the 'base' annotation,
   *         otherwise null, (for the  'base' annotation class name null is returned as well)
   */
  @Nullable
  public String getAnnotationPattern(String annotationClassName) {
    processAnnotation(annotationClassName);
    return myAnnotationNameToPatternMap.get(annotationClassName);
  }

  private void processAnnotation(String annotationClassName) {
    if (!myProcessedAnnotations.add(annotationClassName)) {
      return;
    }
    try {
      final InputStream is = myClassFinder.getClassBytesAsStream(annotationClassName);
      if (is != null) {
        try {
          final Ref<String> patternString = new Ref<String>(null);
          // dig into annotation class and check if it is annotated with pattern annotation.
          // if yes, load the pattern string from the pattern annotation and associate it with this annotation
          final ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
              if (patternString.get() != null || !myPatternAnnotationClassName.equals(Type.getType(desc).getClassName())) {
                return null; // already found or is not pattern annotation
              }
              // dig into pattern annotation in order to discover the pattern string
              return new AnnotationVisitor(Opcodes.ASM4) {
                public void visit(@NonNls String name, Object value) {
                  if ("value".equals(name) && value instanceof String) {
                    patternString.set((String)value);
                  }
                }
              };
            }
          };
          new ClassReader(is).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

          final String pattern = patternString.get();
          if (pattern != null) {
            myAnnotationNameToPatternMap.put(annotationClassName, pattern);
          }
        }
        finally {
          is.close();
        }
      }
    }
    catch (IOException ignored) {
      // todo
    }
  }

  void registerError(String methodName, String operationName, Throwable e) {
    if (myPostponedError == null) {
      // throw the first error that occurred
      Throwable err = e.getCause();
      if (err == null) {
        err = e;
      }
      myPostponedError = new RuntimeException("Operation '" + operationName + "' failed for " + myClassName + "." + methodName + "(): " + err.getMessage(), err);
    }
    if (myInstrumented) {
      processPostponedErrors();
    }
  }

  private void processPostponedErrors() {
    final RuntimeException error = myPostponedError;
    if (error != null) {
      throw error;
    }
  }

}
