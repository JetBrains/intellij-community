package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.Annotated
import com.intellij.workspaceModel.codegen.deft.meta.ObjAnnotation
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.impl.writer.EqualsBy

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

internal val OwnProperty<*, *>.safeAnnotations: List<ObjAnnotation>
  get() = (this as? Annotated)?.annotations ?: emptyList()

internal val OwnProperty<*, *>.isReplaceBySourceKey: Boolean
  get() = safeAnnotations.any { it.fqName == EqualsBy.decoded } || isKey



