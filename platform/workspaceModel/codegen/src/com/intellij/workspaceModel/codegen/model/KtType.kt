// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.codegen.deft.*

class KtType(
  val classifierRange: SrcRange,
  val args: List<KtType> = listOf(),
  val optional: Boolean = false,
  annotations: List<KtAnnotation> = listOf()
) {
  val annotations: KtAnnotations = KtAnnotations().also { it.list.addAll(annotations) }
  val classifier: String get() = classifierRange.text.replace(" ", "")

  override fun toString(): String =
    (if (annotations.list.isEmpty()) "" else annotations.list.joinToString(" ") + " ") +
    if (args.isEmpty()) classifier
    else "$classifier<${args.joinToString(", ")}>"

  fun build(scope: KtScope, diagnostics: Diagnostics, annotations: KtAnnotations = KtAnnotations(), keepUnknownFields: Boolean): ValueType<*>? {
    val childAnnotation = this.annotations.byName[Child::class.java.simpleName]
                          ?: annotations.byName[Child::class.java.simpleName]

    val result = when (classifier) {
      "String" -> TString
      "Int" -> TInt
      "Boolean" -> TBoolean
      "List" -> {
        if (optional) {
          diagnostics.add(classifierRange, "Nullable lists are not supported")
          null
        } else {
          val target = args.singleOrNull()
          if (target == null) diagnostics.add(classifierRange, "List should have 1 type argument: $this")
          val elementType = target?.build(scope, diagnostics, annotations, keepUnknownFields)
          if (elementType != null) TList(elementType) else null
        }
      }
      "Set" -> {
        if (optional) {
          diagnostics.add(classifierRange, "Nullable sets are not supported")
          null
        } else {
          val target = args.singleOrNull()
          if (target == null) diagnostics.add(classifierRange, "Set should have 1 type argument: $this")
          val elementType = target?.build(scope, diagnostics, annotations, keepUnknownFields)
          if (elementType == null) {
            null
          } else if (elementType is TRef<*>) {
            diagnostics.add(classifierRange, "Set of references is not supported")
            null
          } else {
            TSet(elementType)
          }
        }
      }
      "Map" -> {
        if (args.size != 2) {
          diagnostics.add(classifierRange, "Map should have 2 type arguments: $this")
          null
        }
        else {
          val (k, v) = args
          val kt = k.build(scope, diagnostics, annotations, keepUnknownFields)
          val vt = v.build(scope, diagnostics, annotations, keepUnknownFields)
          if (kt != null && vt != null) TMap(kt, vt) else null
        }
      }
      else -> {
        val ktInterface = scope.resolve(classifier)?.ktInterface
        if (ktInterface?.kind == null && classifier !in listOf("VirtualFileUrl", "EntitySource", "PersistentEntityId") && !keepUnknownFields) {
          diagnostics.add(classifierRange, "Unsupported type: $this. " +
                                           "Supported: String, Int, Boolean, List, Set, Map, Serializable, subtypes of Obj")
          null
        }
        else {
          var kind = if (classifier in listOf("VirtualFileUrl", "PersistentEntityId")) WsEntityInterface() else ktInterface?.kind
          if (kind == null && keepUnknownFields) {
            kind = WsUnknownType
          }
          kind?.buildValueType(ktInterface, diagnostics, this, childAnnotation)
        }
      }
    }
    if (result == null) return null

    if (childAnnotation != null && result !is TRef) {
      diagnostics.add(childAnnotation.name, "@Child allowed only on references")
    }

    return if (optional) TOptional(result) else result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KtType

    if (args != other.args) return false
    if (classifier != other.classifier) return false

    return true
  }

  override fun hashCode(): Int {
    var result = args.hashCode()
    result = 31 * result + classifier.hashCode()
    return result
  }
}