package org.jetbrains.protocolReader

import java.lang.reflect.Method

fun appendMethodSignatureJava(method: Method, paramNames: List<String>, out: TextOutput) {
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
      writeJavaTypeName(arg, out)
      out.space().append(paramNames.get(i))
      i++
    }
  }
  out.append(')')
}

fun writeMethodDeclarationJava(out: TextOutput, method: Method) {
  writeMethodDeclarationJava(out, method, listOf<String>())
}

fun writeMethodDeclarationJava(out: TextOutput, m: Method, paramNames: List<String>) {
  out.append("@Override").newLine().append("public ")
  writeJavaTypeName(m.getGenericReturnType(), out)
  out.space()
  appendMethodSignatureJava(m, paramNames, out)
}

trait MethodHandler {
  fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput)
}
