package com.intellij.workspaceModel.codegen

import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.model.KtObjModule
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.Field
import com.intellij.workspaceModel.codegen.deft.MemberOrExtField

val TStructure<*, *>.refsFields: List<Field<out Obj, Any?>>
  get() = newFields.filter { it.name != "parent" && it.type.isRefType() }

val TStructure<*, *>.allRefsFields: List<Field<out Obj, Any?>>
  get() = allFields.filter { it.name != "parent" && it.type.isRefType() }

val TStructure<*, *>.vfuFields: List<Field<out Obj, Any?>>
  get() = newFields.filter { it.type.isVfuType() }

fun ValueType<*>.getRefType(): TRef<*> = when (this) {
  is TRef<*> -> this
  is TOptional<*> -> type.getRefType()
  is TCollection<*, *> -> elementType.getRefType()
  else -> error("Unsupported type of requester, should be called only if `isRefType` is true")
}

fun ValueType<*>.isRefType(): Boolean = when (this) {
  is TRef<*> -> true
  is TOptional<*> -> type.isRefType()
  is TCollection<*, *> -> elementType.isRefType()
  else -> false
}

fun ValueType<*>.isRefTypeWithoutList(): Boolean = when (this) {
  is TRef<*> -> true
  is TOptional<*> -> type.isRefType()
  else -> false
}

fun ValueType<*>.isList(): Boolean = when (this) {
  is TList<*> -> true
  is TOptional<*> -> type.isList()
  else -> false
}

fun ValueType<*>.isVfuType(): Boolean = when (this) {
  is TBlob<*> -> javaSimpleName == "VirtualFileUrl"
  is TOptional<*> -> type.isVfuType()
  is TCollection<*, *> -> elementType.isVfuType()
  else -> false
}

fun MemberOrExtField<*, *>.getParentField(): MemberOrExtField<*, *> {
  val refType = type.getRefType()
  if (refType.child) error("This method should be called on parent reference")

  val declaredReferenceFromChild = refType.targetObjType.structure.refsFields.filter { it.type.getRefType().targetObjType == owner && it != this } +
                                   (refType.targetObjType.module as KtObjModule).extFields.filter { it.type.getRefType().targetObjType == owner && it.owner == refType.targetObjType && it != this }
  if (declaredReferenceFromChild.isEmpty()) {
    error("Reference should be declared at both entities. It exist at ${owner.name}#$name but absent at ${refType.targetObjType.name}")
  }
  if (declaredReferenceFromChild.size > 1) {
    error(
      "More then one reference to ${owner.name} declared at ${declaredReferenceFromChild[0].owner}#${declaredReferenceFromChild[0].name}," +
      "${declaredReferenceFromChild[1].owner}#${declaredReferenceFromChild[1].name}")
  }
  return declaredReferenceFromChild[0]
}

fun sups(vararg extensions: String?): String = extensions.filterNotNull().joinToString(separator = ", ")
