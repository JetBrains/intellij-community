// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

abstract class MethodHandler {
  abstract void writeMethodImplementationJava(ClassScope classScope, Method m, TextOutput out);

  protected static void appendMethodSignatureJava(@NotNull Method method, @NotNull List<String> paramNames, @NotNull TextOutput out) {
    out.append(method.getName()).append('(');
    boolean firstArg = true;
    Type[] types = method.getGenericParameterTypes();
    for (int i = 0, length = types.length; i < length; i++) {
      Type arg = types[i];
      if (firstArg) {
        firstArg = false;
      }
      else {
        out.comma();
      }
      Util.writeJavaTypeName(arg, out);
      out.space().append(paramNames.get(i));
    }
    out.append(')');
  }

  protected static void writeMethodDeclarationJava(TextOutput out, Method method) {
    writeMethodDeclarationJava(out, method, Collections.<String>emptyList());
  }

  protected static void writeMethodDeclarationJava(@NotNull TextOutput out, @NotNull Method m, @NotNull List<String> paramNames) {
    out.append("@Override").newLine().append("public ");
    Util.writeJavaTypeName(m.getGenericReturnType(), out);
    out.space();
    appendMethodSignatureJava(m, paramNames, out);
  }
}
