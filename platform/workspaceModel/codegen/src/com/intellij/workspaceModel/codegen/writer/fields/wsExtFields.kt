package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.EntityStorage
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
        line("val diff = (this as ${ModifiableWorkspaceEntityBase::class.fqn}<*>).diff")
        `if`("diff != null") {
          when (type) {
            is TOptional<*> -> {
              `if`("value != null") {
                `if`("(value as ${oppositeField.owner.javaImplFqn}.Builder).diff == null") {
                  if (oppositeList) {
                    line("value.${oppositeField.implFieldName} = (value.${oppositeField.implFieldName} ?: emptyList()) + this")
                  }
                  else {
                    line("value.${oppositeField.implFieldName} = this")
                  }
                  line("diff.addEntity(value)")
                }
              }
            }
            is TRef<*> -> {
              `if`("(value as ${oppositeField.owner.javaImplFqn}.Builder).diff == null") {
                if (oppositeList) {
                  line("value.${oppositeField.implFieldName} = (value.${oppositeField.implFieldName} ?: emptyList()) + this")
                }
                else {
                  line("value.${oppositeField.implFieldName} = this")
                }
                line("diff.addEntity(value)")
              }
            }
            is TList<*> -> {
              `for`("item in value") {
                `if`("(item as ${oppositeField.owner.javaImplFqn}.Builder).diff == null") {
                  if (oppositeList) {
                    line("item.${oppositeField.implFieldName} = (item.${oppositeField.implFieldName} ?: emptyList()) + this")
                  }
                  else {
                    line("item.${oppositeField.implFieldName} = this")
                  }
                  line("diff.addEntity(item)")
                }
              }
            }
          }
          line("diff.$updateFunction(${oppositeField.owner.javaImplName}.${oppositeField.refsConnectionId}, this, value)")
        }
        `else` {
          line("val key = ${
            ExtRefKey::class.fqn
          }(\"${oppositeField.owner.javaSimpleName}\", \"${oppositeField.javaName}\", ${isChild}, ${oppositeField.owner.javaImplName}.${oppositeField.refsConnectionId})")
          line("this.extReferences[key] = value")
          line()
          when (type) {
            is TOptional<*> -> {
              `if`("value != null") {
                if (oppositeList) {
                  line(
                    "(value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = ((value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} ?: emptyList()) + this")
                }
                else {
                  line("(value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = this")
                }
              }
            }
            is TRef<*> -> {
              if (oppositeList) {
                line(
                  "(value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = ((value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} ?: emptyList()) + this")
              }
              else {
                line("(value as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = this")
              }
            }
            is TList<*> -> {
              `for`("item in value") {
                if (oppositeList) {
                  line(
                    "(item as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = ((item as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} ?: emptyList()) + this")
                }
                else {
                  line("(item as ${oppositeField.owner.javaImplFqn}.Builder).${oppositeField.implFieldName} = this")
                }
              }
            }
          }
        }
      }
    }
  }
