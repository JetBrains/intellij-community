// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.TRef
import com.intellij.workspaceModel.codegen.deft.ValueType

object ObjInterface : KtInterfaceKind() {
  override fun buildField(
    fieldNumber: Int,
    field: DefField,
    scope: KtScope,
    type: DefType,
    diagnostics: Diagnostics,
    keepUnknownFields: Boolean,
  ) {
    field.id = fieldNumber + 1 // todo: persistent ids
    field.toMemberField(scope, type, diagnostics, keepUnknownFields)
  }

  override fun buildValueType(
    ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
    childAnnotation: KtAnnotation?
  ): ValueType<*>? {
    val type = ktInterface?.objType
    if (type == null) {
      diagnostics.add(
        ktType.classifierRange, "Unsupported type: $ktType. " +
                                "Supported: String, Int, Boolean, List, Map, Serializable, subtypes of Obj"
      )
      return null
    }

    return TRef<Obj>(type.id, child = childAnnotation != null).also {
      it.targetObjType = type
    }
  }
}