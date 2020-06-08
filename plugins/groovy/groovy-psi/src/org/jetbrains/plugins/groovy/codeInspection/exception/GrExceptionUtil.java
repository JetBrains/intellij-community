// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.exception;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public final class GrExceptionUtil {
  public static boolean ignore(@NotNull GrParameter parameter) {
    final String name = StringUtil.toLowerCase(parameter.getName());
    return "ignore".equals(name) || "ignored".equals(name);
  }
}
