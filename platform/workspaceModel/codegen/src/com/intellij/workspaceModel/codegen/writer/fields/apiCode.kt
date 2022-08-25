package storage.codegen.field

import deft.storage.codegen.*
import deft.storage.codegen.field.javaBuilderType
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.api: String
  get() = "${override(isOverride)}val $javaName: ${type.javaType}"

val Field<*, *>.builderApi: String
  get() = "override var $javaName: ${type.javaBuilderType}"
