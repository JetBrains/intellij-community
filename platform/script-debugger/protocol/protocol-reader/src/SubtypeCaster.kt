package org.jetbrains.protocolReader

/**
 * An internal facility for navigating from object of base type to object of subtype. Used only
 * when user wants to parse JSON object as subtype.
 */
abstract class SubtypeCaster(private val subtypeRef: TypeRef<*>) {
  abstract fun writeJava(out: TextOutput)

  fun getSubtypeHandler() = subtypeRef.type!!
}