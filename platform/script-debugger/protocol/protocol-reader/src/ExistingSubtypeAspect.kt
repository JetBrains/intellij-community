package org.jetbrains.protocolReader

class ExistingSubtypeAspect(private val jsonSuperClass: TypeRef<*>) {
  private var subtypeCaster: SubtypeCaster? = null

  public fun setSubtypeCaster(subtypeCaster: SubtypeCaster) {
    this.subtypeCaster = subtypeCaster
  }

  fun writeGetSuperMethodJava(out: TextOutput) {
    out.newLine().append("@Override").newLine().append("public ").append(jsonSuperClass.type!!.typeClass.getCanonicalName()).append(" getSuper()").openBlock()
    out.append("return ").append(BASE_VALUE_PREFIX).semi().closeBlock()
  }

  fun writeSuperFieldJava(out: TextOutput) {
    out.newLine().append("private final ").append(jsonSuperClass.type!!.typeClass.getCanonicalName()).append(' ').append(BASE_VALUE_PREFIX).semi().newLine()
  }

  fun writeSuperConstructorParamJava(out: TextOutput) {
    out.comma().append(jsonSuperClass.type!!.typeClass.getCanonicalName()).append(' ').append(BASE_VALUE_PREFIX)
  }

  fun writeSuperConstructorInitialization(out: TextOutput) {
    out.append("this.").append(BASE_VALUE_PREFIX).append(" = ").append(BASE_VALUE_PREFIX).append(';').newLine().newLine()
  }

  fun writeParseMethod(className: String, scope: ClassScope, out: TextOutput) {
    out.newLine().append("public static ").append(className).space().append("parse").append('(').append(JSON_READER_PARAMETER_DEF).append(", String name").append(')').openBlock()
    out.append("return ")
    jsonSuperClass.type!!.writeInstantiateCode(scope, out)
    out.append('(').append(READER_NAME).append(", name)").append('.')
    subtypeCaster!!.writeJava(out)
    out.semi().closeBlock()
    out.newLine()
  }

  public fun writeInstantiateCode(className: String, out: TextOutput) {
    out.append(className).append(".parse")
  }
}
