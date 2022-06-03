package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.fields.javaType
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.codegen.classes.`else`
import com.intellij.workspaceModel.codegen.classes.`for`
import com.intellij.workspaceModel.codegen.classes.`if`
import com.intellij.workspaceModel.codegen.getRefType
import com.intellij.workspaceModel.codegen.utils.*
import com.intellij.workspaceModel.codegen.deft.TList
import com.intellij.workspaceModel.codegen.deft.TOptional
import com.intellij.workspaceModel.codegen.deft.TRef
import com.intellij.workspaceModel.codegen.deft.ExtField

val ExtField<*, *>.wsCode: String
  get() = lines {
    val isChild = type.getRefType().child
    val annotation = if (isChild) "@${Child::class.fqn} " else ""
    val oppositeField = referencedField
    val isNullable = type is TOptional<*>
    val isList = type is TList<*>
    val singleFunction = if (!isList) if (isNullable) ".singleOrNull()" else ".single()" else ""
    val updateFunction = if (isList) {
      fqn4(EntityStorage::updateOneToManyChildrenOfParent)
    }
    else {
      if (isChild) fqn3(EntityStorage::updateOneToOneChildOfParent) else fqn3(EntityStorage::updateOneToOneParentOfChild)
    }
    val oppositeList = oppositeField.type is TList<*>
    val referrFunction = if (oppositeList) "referrersy" else "referrersx"
    sectionNoBrackets("var ${owner.javaBuilderName}.$name: $annotation${type.javaType}") {
      section("get()") {
        line("return ${wsFqn(referrFunction)}(${oppositeField.owner.javaSimpleName}::${oppositeField.javaName})$singleFunction")
      }
      section("set(value)") {
        line("(this as ${ModifiableReferableWorkspaceEntity::class.fqn}).linkExternalEntity(${oppositeField.owner.name}::class, if (value is List<*>) value as List<${WorkspaceEntity::class.fqn}?> else listOf(value) as List<${WorkspaceEntity::class.fqn}?> )")
      }
    }
  }
