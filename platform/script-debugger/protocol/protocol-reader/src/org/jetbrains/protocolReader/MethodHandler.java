// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Iterator;

abstract class MethodHandler {
  abstract void writeMethodImplementationJava(ClassScope classScope, Method m, TextOutput out);

  protected static void appendMethodSignatureJava(Method m, Iterable<String> paramNames, TextOutput out) {
    out.append(m.getName()).append('(');
    boolean firstArg = true;
    Iterator<String> namesIt = paramNames.iterator();
    for (Type arg : m.getGenericParameterTypes()) {
      if (firstArg) {
        firstArg = false;
      }
      else {
        out.comma();
      }
      Util.writeJavaTypeName(arg, out);
      out.append(' ').append(namesIt.next());
    }
    out.append(')');
  }

  protected static void writeMethodDeclarationJava(TextOutput out, Method m) {
    writeMethodDeclarationJava(out, m, Collections.<String>emptyList());
  }

  protected static void writeMethodDeclarationJava(TextOutput out, Method m, Iterable<String> paramNames) {
    out.append("@Override").newLine().append("public ");
    Util.writeJavaTypeName(m.getGenericReturnType(), out);
    out.append(' ');
    appendMethodSignatureJava(m, paramNames, out);
  }
}
