package org.jetbrains.protocolReader

import java.util.concurrent.atomic.AtomicReferenceArray

internal class VolatileFieldBinding(private val position: Int, private val fieldTypeInfo: (scope: FileScope, out: TextOutput) -> Unit) {
  fun get(atomicReferenceArray: AtomicReferenceArray<Any>) = atomicReferenceArray.get(position)

  fun writeGetExpression(out: TextOutput) {
    out.append("lazy_").append(position)
  }

  fun writeFieldDeclaration(scope: ClassScope, out: TextOutput) {
    out.append("private var ")
    writeGetExpression(out)
    out.append(": ")
    fieldTypeInfo(scope, out)
    out.append("? = null")
  }
}
