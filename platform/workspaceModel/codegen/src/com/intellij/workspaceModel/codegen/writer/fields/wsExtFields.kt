package org.jetbrains.deft.codegen.ijws.fields

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.impl.*
import deft.storage.codegen.*
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.codegen.ijws.classes.`else`
import org.jetbrains.deft.codegen.ijws.classes.`for`
import org.jetbrains.deft.codegen.ijws.classes.`if`
import org.jetbrains.deft.codegen.ijws.getRefType
import org.jetbrains.deft.codegen.utils.*
import org.jetbrains.deft.impl.TList
import org.jetbrains.deft.impl.TOptional
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.fields.ExtField

val ExtField<*, *>.wsCode: String
  get() = lines {
    val isChild = type.getRefType().child
    val annotation = if (isChild) "@${Child::class.fqn} " else ""
    val oppositeField = referencedField
    val isNullable = type is TOptional<*>
    val isList = type is TList<*>
    val singleFunction = if (!isList) if (isNullable) ".singleOrNull()" else ".single()" else ""
    val updateFunction = if (isList) {
      fqn4(WorkspaceEntityStorage::updateOneToManyChildrenOfParent)
    }
    else {
      if (isChild) fqn3(WorkspaceEntityStorage::updateOneToOneChildOfParent) else fqn3(WorkspaceEntityStorage::updateOneToOneParentOfChild)
    }
    val oppositeList = oppositeField.type is TList<*>
    val referrFunction = if (oppositeList) "referrersy" else "referrersx"
    sectionNoBrackets("var ${owner.javaBuilderName}.$name: $annotation${type.javaType}") {
      section("get()") {
        line("return ${wsFqn(referrFunction)}(${oppositeField.owner.javaSimpleName}::${oppositeField.javaName})$singleFunction")
      }
      section("set(value)") {
        line("val diff = (this as ${owner.javaImplFqn}.Builder).diff")
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
