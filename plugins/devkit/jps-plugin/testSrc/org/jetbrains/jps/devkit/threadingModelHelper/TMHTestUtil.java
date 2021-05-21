// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public class TMHTestUtil {
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
}
