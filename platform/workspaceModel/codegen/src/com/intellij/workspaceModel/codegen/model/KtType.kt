// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*

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

  fun build(scope: KtScope, diagnostics: Diagnostics, annotations: KtAnnotations = KtAnnotations()): ValueType<*>? {
    val childAnnotation = this.annotations.byName[Child::class.java.simpleName]
                          ?: annotations.byName[Child::class.java.simpleName]

    val result = when (classifier) {
      "String" -> TString
      "Int" -> TInt
      "Boolean" -> TBoolean
      "List" -> {
        val target = args.singleOrNull()
        if (target == null) diagnostics.add(classifierRange, "List should have 1 type argument: $this")
        val elementType = target?.build(scope, diagnostics, annotations)
        if (elementType != null) TList(elementType) else null
      }
      "Map" -> {
        if (args.size != 2) {
          diagnostics.add(classifierRange, "Map should have 2 type arguments: $this")
          null
        }
        else {
          val (k, v) = args
          val kt = k.build(scope, diagnostics, annotations)
          val vt = v.build(scope, diagnostics, annotations)
          if (kt != null && vt != null) TMap(kt, vt) else null
        }
      }
      else -> {
        val ktInterface = scope.resolve(classifier)?.ktInterface
        if (ktInterface?.kind == null && classifier !in listOf("VirtualFileUrl", "EntitySource", "PersistentEntityId")) {
          diagnostics.add(classifierRange, "Unsupported type: $this. " +
                                           "Supported: String, Int, Boolean, List, Map, Serializable, subtypes of Obj")
          null
        }
        else {
          val kind = if (classifier in listOf("VirtualFileUrl", "PersistentEntityId")) WsEntityInterface() else ktInterface?.kind
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