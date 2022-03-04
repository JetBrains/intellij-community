package org.jetbrains.deft.codegen.ijws.interfaces

import deft.storage.codegen.field.javaType
import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.wsBuilderApi: String
    get() = "override var $javaName: ${type.javaType}"