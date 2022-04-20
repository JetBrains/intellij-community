package storage.codegen.field

import deft.storage.codegen.*
import deft.storage.codegen.field.javaBuilderType
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.api: String
  get() = buildString {
    append(apiBlocking)
    if (suspendable == true) append("\n$apiSuspendable")
  }

val Field<*, *>.apiBlocking: String
  get() = "${override(isOverride)}val $javaName: ${type.javaType}"

val Field<*, *>.apiSuspendable: String
  get() = "suspend fun $suspendableGetterName(): ${type.javaType}"

val Field<*, *>.builderApi: String
  get() = "override var $javaName: ${type.javaBuilderType}"
