// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.jetbrains.protocolReader


import java.lang.reflect.Method

abstract class MethodHandler {
  abstract fun writeMethodImplementationJava(classScope: ClassScope, m: Method, out: TextOutput)

  class object {

    protected fun appendMethodSignatureJava(method: Method, paramNames: List<String>, out: TextOutput) {
      out.append(method.getName()).append('(')
      var firstArg = true
      val types = method.getGenericParameterTypes()
      run {
        var i = 0
        val length = types.size
        while (i < length) {
          val arg = types[i]
          if (firstArg) {
            firstArg = false
          }
          else {
            out.comma()
          }
          Util.writeJavaTypeName(arg, out)
          out.space().append(paramNames.get(i))
          i++
        }
      }
      out.append(')')
    }

    protected fun writeMethodDeclarationJava(out: TextOutput, method: Method) {
      writeMethodDeclarationJava(out, method, listOf<String>())
    }

    protected fun writeMethodDeclarationJava(out: TextOutput, m: Method, paramNames: List<String>) {
      out.append("@Override").newLine().append("public ")
      Util.writeJavaTypeName(m.getGenericReturnType(), out)
      out.space()
      appendMethodSignatureJava(m, paramNames, out)
    }
  }
}
