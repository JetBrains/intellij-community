// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TMHTestUtil {
  private static final String REQUIRES_EDT_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresEdt";
  private static final String REQUIRES_BACKGROUND_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresBackgroundThread";
  private static final String REQUIRES_READ_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresReadLock";
  private static final String REQUIRES_WRITE_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresWriteLock";
  private static final String REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME =
    "com/intellij/util/concurrency/annotations/fake/RequiresReadLockAbsence";
  private static final String APPLICATION_MANAGER_CLASS_NAME = "com/intellij/openapi/application/fake/ApplicationManager";
  private static final String APPLICATION_CLASS_NAME = "com/intellij/openapi/application/fake/Application";

  public static void printDebugInfo(byte[] classData, byte[] instrumentedClassData) {
    System.out.println(classDataToText(classData));
    System.out.println();
    System.out.println(classDataToText(instrumentedClassData));
  }

  public static @NotNull String classDataToText(byte[] data) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    @SuppressWarnings("ImplicitDefaultCharsetUsage")
    PrintWriter printWriter = new PrintWriter(buffer);
    TraceClassVisitor visitor = new TraceClassVisitor(printWriter);
    new ClassReader(data).accept(visitor, 0);
    return buffer.toString();
  }

  public static boolean containsMethodCall(byte @NotNull [] classBytes, final @NotNull String methodName) {
    Ref<Boolean> contains = Ref.create(Boolean.FALSE);
    ClassVisitor visitor = new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access,
                                       String name,
                                       String descriptor,
                                       String signature,
                                       String[] exceptions) {
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals(methodName)) {
              contains.set(Boolean.TRUE);
            }
          }
        };
      }
    };
    ClassReader reader = new ClassReader(classBytes);
    reader.accept(visitor, 0);
    return contains.get();
  }

  public static List<Integer> getLineNumbers(byte @NotNull [] classBytes) {
    List<Integer> lineNumbers = new ArrayList<>();
    ClassVisitor visitor = new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access,
                                       String name,
                                       String descriptor,
                                       String signature,
                                       String[] exceptions) {
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitLineNumber(int line, Label start) {
            lineNumbers.add(line);
          }
        };
      }
    };
    ClassReader reader = new ClassReader(classBytes);
    reader.accept(visitor, 0);
    return lineNumbers;
  }

  public static byte @Nullable [] instrument(byte @NotNull [] classData) {
    FailSafeClassReader reader = new FailSafeClassReader(classData);
    int flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(reader));
    ClassWriter writer = new ClassWriter(reader, flags);
    boolean instrumented = TMHInstrumenter.instrument(reader, writer, Set.of(
      new TMHAssertionGenerator.AssertEdt(REQUIRES_EDT_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertBackgroundThread(REQUIRES_BACKGROUND_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertReadAccess(REQUIRES_READ_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertWriteAccess(REQUIRES_WRITE_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertNoReadAccess(REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME)
    ), true);
    return instrumented ? writer.toByteArray() : null;
  }
}
