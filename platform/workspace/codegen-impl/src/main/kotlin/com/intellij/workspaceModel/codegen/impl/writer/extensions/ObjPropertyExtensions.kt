package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

internal val ObjProperty<*, *>.hasSetter: Boolean
  get() = open || valueKind == ObjProperty.ValueKind.Plain

internal val ObjProperty<*, *>.javaName: String
  get() = name

internal val ObjProperty<*, *>.isOverride: Boolean
  get() = receiver.allSuperClasses.any { name in it.fieldsByName }


internal val ObjProperty<*, *>.isComputable: Boolean
  get() = valueKind is ObjProperty.ValueKind.Computable

internal val ObjProperty<*, *>.withDefault: Boolean
  get() = valueKind is ObjProperty.ValueKind.WithDefault



