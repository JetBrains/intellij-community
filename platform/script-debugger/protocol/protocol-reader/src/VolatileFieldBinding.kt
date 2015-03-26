package org.jetbrains.protocolReader

import java.util.concurrent.atomic.AtomicReferenceArray

class VolatileFieldBinding(private val position: Int, private val fieldTypeInfo: (scope: FileScope, out: TextOutput)->Unit) {
  public fun get(atomicReferenceArray: AtomicReferenceArray<Any>): Any {
    return atomicReferenceArray.get(position)
  }

  fun writeGetExpression(out: TextOutput) {
    out.append("lazy_").append(position)
  }

  fun writeFieldDeclaration(scope: ClassScope, out: TextOutput) {
    out.append("private ")
    fieldTypeInfo(scope, out)
    out.space()
    writeGetExpression(out)
    out.semi()
  }
}
