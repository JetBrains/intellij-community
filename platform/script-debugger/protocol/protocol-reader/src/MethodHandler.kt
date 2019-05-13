package org.jetbrains.protocolReader

import java.lang.reflect.Method

internal fun appendMethodSignatureJava(method: Method, paramNames: List<String>, out: TextOutput) {
  out.append(method.name).append('(')
  var firstArg = true
  val types = method.genericParameterTypes
  for (i in 0..types.size - 1) {
    val arg = types[i]
    if (firstArg) {
      firstArg = false
    }
    else {
      out.comma()
    }
    out.append(paramNames.get(i))
    out.append(": ")
    writeJavaTypeName(arg, out)
  }
  out.append(')')
}

fun writeMethodDeclarationJava(out: TextOutput, m: Method, paramNames: List<String> = listOf<String>()) {
  out.append("override fun ")
  appendMethodSignatureJava(m, paramNames, out)
}

internal interface MethodHandler {
  fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput)
}
