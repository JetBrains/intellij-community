// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.exception;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public final class GrExceptionUtil {
  private static final @NlsSafe String IGNORE = "ignore";
  private static final @NlsSafe String IGNORED = "ignored";

  public static boolean ignore(@NotNull GrParameter parameter) {
    final String name = StringUtil.toLowerCase(parameter.getName());
    return IGNORE.equals(name) || IGNORED.equals(name);
  }
}
