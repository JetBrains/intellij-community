package org.jetbrains.protocolReader

import java.lang.reflect.Method

fun appendMethodSignatureJava(method: Method, paramNames: List<String>, out: TextOutput) {
  out.append(method.getName()).append('(')
  var firstArg = true
  val types = method.getGenericParameterTypes()
  for (i in 0..types.size() - 1) {
    val arg = types[i]
    if (firstArg) {
      firstArg = false
    }
    else {
      out.comma()
    }
    writeJavaTypeName(arg, out)
    out.space().append(paramNames.get(i))
  }
  out.append(')')
}

fun writeMethodDeclarationJava(out: TextOutput, m: Method, paramNames: List<String> = listOf<String>()) {
  out.append("@Override").newLine().append("public ")
  writeJavaTypeName(m.getGenericReturnType(), out)
  out.space()
  appendMethodSignatureJava(m, paramNames, out)
}

interface MethodHandler {
  fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput)
}
