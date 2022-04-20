package org.jetbrains.deft.codegen.kotlin.writer.toplevel

import deft.storage.codegen.field.defCode
import deft.storage.codegen.field.javaType
import deft.storage.codegen.javaBuilderName
import deft.storage.codegen.javaFullName
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.impl.fields.ExtField

val ExtField<*, *>.code: String
  get() = buildString {
    append("val ${owner.javaFullName}.Companion.$name: ${ExtField::class.java.fqn}<${owner.javaFullName}, ${type.javaType}> ")
    append("by ${id.moduleId.objName}.defExt(${id.localId}, ${owner.javaFullName}, ${type.defCode})")
    append("\n")
    if (open) {
      append("var ${owner.javaBuilderName}.$name: ${type.javaType} by $metaRef")
      append("\n")
    }
    append("\n") // empty line
  }

val ExtField<*, *>.metaRef: String
  get() = "${owner.javaFullName}.$name"