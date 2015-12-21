package org.jetbrains.protocolReader

private val BASE_VALUE_PREFIX = "baseMessage"

internal class ExistingSubtypeAspect(private val jsonSuperClass: TypeRef<*>) {
  private var subtypeCaster: SubtypeCaster? = null

  fun setSubtypeCaster(subtypeCaster: SubtypeCaster) {
    this.subtypeCaster = subtypeCaster
  }

  fun writeGetSuperMethodJava(out: TextOutput) {
    out.newLine().append("override fun getBase() = ").append(BASE_VALUE_PREFIX)
  }

  fun writeSuperFieldJava(out: TextOutput) {
    out.append(", private val ").append(BASE_VALUE_PREFIX).append(": ").append(jsonSuperClass.type!!.typeClass.canonicalName)
  }

  fun writeParseMethod(scope: ClassScope, out: TextOutput) {
    out.newLine().append("companion object").block {
      out.append("fun parse").append('(').append(JSON_READER_PARAMETER_DEF).append(", name: String?").append(") = ")
      jsonSuperClass.type!!.writeInstantiateCode(scope, out)
      out.append('(').append(READER_NAME).append(", name)").append('.')
      subtypeCaster!!.writeJava(out)
    }
    out.newLine()
  }

  fun writeInstantiateCode(className: String, out: TextOutput) {
    out.append(className).append(".parse")
  }
}
