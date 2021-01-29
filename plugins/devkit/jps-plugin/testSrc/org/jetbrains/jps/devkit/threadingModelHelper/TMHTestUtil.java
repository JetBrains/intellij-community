// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
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
}
