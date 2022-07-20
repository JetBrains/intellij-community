// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.Field

abstract class KtInterfaceKind {
  abstract fun buildField(fieldNumber: Int, field: DefField, scope: KtScope, type: DefType, diagnostics: Diagnostics, keepUnknownFields: Boolean)
  abstract fun buildValueType(
    ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
    childAnnotation: KtAnnotation?
  ): ValueType<*>?
}

open class WsEntityInterface : KtInterfaceKind() {
  override fun buildField(fieldNumber: Int,
                          field: DefField,
                          scope: KtScope,
                          type: DefType,
                          diagnostics: Diagnostics,
                          keepUnknownFields: Boolean) {
    field.toMemberField(scope, type, diagnostics, keepUnknownFields)
    if (fieldNumber == 0) {
      val entitySource = Field(type, "entitySource", TBlob<Any>("EntitySource"))
      entitySource.exDef = field
      entitySource.open = field.open
      if (field.expr) {
        entitySource.hasDefault =
          if (field.suspend) Field.Default.suspend
          else Field.Default.plain
      }
      entitySource.content = field.content
    }
  }

  override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*>? {
    val type = ktInterface?.objType
    return if (type != null)
      TRef<Obj>(type.id, child = childAnnotation != null).also {
        it.targetObjType = type
      }
    else if (ktType.classifier in listOf("VirtualFileUrl", "EntitySource", "PersistentEntityId"))
      TBlob<Any>(ktType.classifier)
    else {
      diagnostics.add(ktType.classifierRange, "Unsupported type: $ktType. " +
                                              "Supported: String, Int, Boolean, List, Set, Map, Serializable, Enum, Data and Sealed classes, subtypes of Obj")
      null
    }
  }
}

object WsPsiEntityInterface: WsEntityInterface() {
  override fun buildValueType(ktInterface: KtInterface?,
                              diagnostics: Diagnostics,
                              ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*>? {
    val type = ktInterface?.objType
    if (type != null)
      return TPsiRef<Obj>(type.id, child = childAnnotation != null).also { it.targetObjType = type }
    return super.buildValueType(ktInterface, diagnostics, ktType, childAnnotation)
  }
}

object WsEntityWithPersistentId: WsEntityInterface()

object WsUnknownType: WsEntityInterface() {
  override fun buildValueType(ktInterface: KtInterface?,
                              diagnostics: Diagnostics,
                              ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*> {
    return TBlob<Any>(ktType.classifier)
  }
}

interface WsPropertyClass

object WsEnum : WsEntityInterface(), WsPropertyClass {
  override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*> {
    return TBlob<Any>(ktType.classifier)
  }
}

object WsData : WsEntityInterface(), WsPropertyClass {
  override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*> {
    return TBlob<Any>(ktType.classifier)
  }
}

object WsSealed : WsEntityInterface(), WsPropertyClass {
  override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*> {
    return TBlob<Any>(ktType.classifier)
  }
}

object WsObject : WsEntityInterface(), WsPropertyClass {
  override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                              childAnnotation: KtAnnotation?): ValueType<*> {
    return TBlob<Any>(ktType.classifier)
  }
}