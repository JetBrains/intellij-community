// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class AnalysisUtils {
  static boolean isClassFile(@NotNull File classFile) {
    return classFile.getPath().endsWith(".class");
  }

  public static String getClassName(File classFile) {
    return StringUtil.trimEnd(classFile.getName(), ".class");
  }

  public static String getSourceToplevelFQName(String classFQVMName) {
    final int index = classFQVMName.indexOf('$');
    if (index > 0) classFQVMName = classFQVMName.substring(0, index);
    classFQVMName = StringUtil.trimStart(classFQVMName, "/");
    return internalNameToFqn(classFQVMName);
  }

  public static @NotNull String internalNameToFqn(@NotNull String internalName) {
    return internalName.replace('\\', '.').replace('/', '.');
  }

  public static @NotNull String fqnToInternalName(@NotNull String fqn) {
    return fqn.replace('.', '/');
  }

  public static @NotNull String buildVMName(@NotNull String packageVMName, @NotNull String simpleName) {
    return packageVMName.isEmpty() ? simpleName : packageVMName + "/" + simpleName;
  }
}
