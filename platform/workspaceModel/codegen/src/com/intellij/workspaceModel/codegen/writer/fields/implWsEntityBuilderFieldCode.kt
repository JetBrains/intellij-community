// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.fields.javaMutableType
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.fields.unsupportedTypeError
import com.intellij.workspaceModel.codegen.classes.*
import com.intellij.workspaceModel.codegen.classes.`else`
import com.intellij.workspaceModel.codegen.classes.`for`
import com.intellij.workspaceModel.codegen.classes.`if`
import com.intellij.workspaceModel.codegen.classes.ifElse
import com.intellij.workspaceModel.codegen.getRefType
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.utils.*
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.ExtField
import com.intellij.workspaceModel.codegen.deft.Field
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

val Field<*, *>.implWsBuilderFieldCode: String
  get() = type.implWsBuilderBlockingCode(this)

private fun ValueType<*>.implWsBuilderBlockingCode(field: Field<*, *>, optionalSuffix: String = ""): String = when (this) {
  TBoolean, TInt -> """
            override var ${field.javaName}: ${field.type.javaMutableType}$optionalSuffix
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
        """.trimIndent()
  TString -> """
            override var ${field.javaName}: ${field.type.javaMutableType}
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
        """.trimIndent()
  is TRef -> {
    val connectionName = field.refsConnectionId
    val getterSetterNames = field.refNames()

    // Opposite field may be either one-to-one or one-to-many

    val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
    lines {
      sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
        section("get()") {
          line("val _diff = diff")
          line("return if (_diff != null) {")
          line("    _diff.${getterSetterNames.getter}($connectionName, this) ?: this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})]$notNullAssertion as$optionalSuffix $javaType")
          line("} else {")
          line("    this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})]$notNullAssertion as$optionalSuffix $javaType")
          line("}")
        }
        section("set(value)") {
          line("checkModificationAllowed()")
          line("val _diff = diff")
          `if`("_diff != null && value is ${ModifiableWorkspaceEntityBase::class.fqn}<*> && value.diff == null") {
            backrefSetup(this@implWsBuilderBlockingCode, field)
            line("_diff.addEntity(value)")
          }
          section("if (_diff != null && (value !is ${ModifiableWorkspaceEntityBase::class.fqn}<*> || value.diff != null))") {
            line("_diff.${getterSetterNames.setter}($connectionName, this, value)")
          }
          section("else") {
            backrefSetup(this@implWsBuilderBlockingCode, field)
            line()
            line("this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] = value")
          }
          line("changedProperty.add(\"${field.javaName}\")")
        }
      }
    }
  }
  is TList<*> -> {
    val elementType = this.elementType
    if (this.isRefType()) {
      val connectionName = field.refsConnectionId
      val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else error("It's prohibited to have nullable reference list")
      if ((elementType as TRef<*>).targetObjType.abstract) {
        lines(indent = "    ") {
          sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
            section("get()") {
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("    _diff.${fqn2(EntityStorage::extractOneToAbstractManyChildren)}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] as? $javaType ?: emptyList())")
              line("} else {")
              line("    this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] as $javaType ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
              line("}")
            }
            section("set(value)") {
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase::class.fqn}<*> && (item_value as? ${
                    ModifiableWorkspaceEntityBase::class.fqn
                  }<*>)?.diff == null") {
                    line("_diff.addEntity(item_value)")
                  }
                }
                line("_diff.${fqn5(EntityStorage::updateOneToAbstractManyChildrenOfParent)}($connectionName, this, value.asSequence())")
              }
              `else` {
                backrefListSetup(elementType, field)
                line()
                line("this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] = value")
              }
              line("changedProperty.add(\"${field.javaName}\")")
            }
          }
        }
      }
      else {
        lines {
          lineComment("List of non-abstract referenced types")
          line("var _${field.javaName}: $javaType? = emptyList()")
          sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
            section("get()") {
              lineComment("Getter of the list of non-abstract referenced types")
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("    _diff.${fqn2(EntityStorage::extractOneToManyChildren)}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] as? $javaType ?: emptyList())")
              line("} else {")
              line(
                "    this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] as? $javaType ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
              line("}")
            }
            section("set(value)") {
              lineComment("Setter of the list of non-abstract referenced types")
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase::class.fqn}<*> && (item_value as? ${ModifiableWorkspaceEntityBase::class.fqn}<*>)?.diff == null") {
                    line("_diff.addEntity(item_value)")
                  }
                }
                line("_diff.${fqn4(EntityStorage::updateOneToManyChildrenOfParent)}($connectionName, this, value)")
              }
              `else` {
                backrefListSetup(elementType, field)
                line()
                line("this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] = value")
              }
              line("changedProperty.add(\"${field.javaName}\")")
            }
          }
        }
      }
    }
    else {
      """
            override var ${field.javaName}: List<${wsFqn(elementType.javaType)}>
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    ${elementType.addVirtualFileIndex(field)}
                    changedProperty.add("${field.javaName}")
                }
                
            """.trimIndent()
    }
  }
  is TSet<*> -> {
    val elementType = this.elementType
    if (this.isRefType()) {
      error("Set of references is not supported")
    } else {
      """
            override var ${field.javaName}: Set<${wsFqn(elementType.javaType)}>
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    ${elementType.addVirtualFileIndex(field)}
                    changedProperty.add("${field.javaName}")
                }
                
            """.trimIndent()
    }
  }
  is TMap<*, *> -> """
            override var ${field.javaName}: $javaType
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
    """.trimIndent()
  is TOptional<*> -> type.implWsBuilderBlockingCode(field, "?")
  is TStructure<*, *> -> "//TODO: ${field.javaName}"
  is TBlob<*> -> """
            override var ${field.javaName}: ${wsFqn(javaType)}$optionalSuffix
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData().${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                    ${
    if (javaType == "VirtualFileUrl")
      """val _diff = diff
      |                    if (_diff != null) index(this, "${field.javaName}", value)
                        """.trimMargin()
    else ""
  }
                }
                
        """.trimIndent()
  else -> unsupportedTypeError()
}

private fun LinesBuilder.backrefSetup(
  valueType: TRef<*>,
  field: Field<*, *>,
  varName: String = "value",
) {
  val referencedField = field.referencedField
  val type = referencedField.type
  val isChild = type.getRefType().child
  when (type) {
    is TList<*> -> {
      lineComment("Setting backref of the list")
      `if`("$varName is ${ModifiableWorkspaceEntityBase::class.fqn}<*>") {
        line("val data = ($varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] as? List<Any> ?: emptyList()) + this")
        line("$varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is TSet<*> -> {
      lineComment("Setting backref of the set")
      `if`("$varName is ${ModifiableWorkspaceEntityBase::class.fqn}<*>") {
        line("val data = ($varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] as? Set<Any> ?: emptySet()) + this")
        line("$varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is TOptional<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase::class.fqn}<*>") {
        line("$varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] = this")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is TRef<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase::class.fqn}<*>") {
        line("$varName.entityLinks[${EntityLink::class.fqn}($isChild, ${field.refsConnectionId})] = this")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    else -> error("Unexpected")
  }
}

private fun LinesBuilder.backrefListSetup(
  valueType: TRef<*>,
  field: Field<*, *>,
  varName: String = "value",
) {
  val itemName = "item_$varName"
  `for`("$itemName in $varName") {
    backrefSetup(valueType, field, itemName)
  }
}

fun LinesBuilder.implWsBuilderIsInitializedCode(field: Field<*, *>) {
  val javaName = field.javaName
  when (field.type) {
    is TCollection<*, *> -> if (field.type.isRefType()) {
      lineComment("Check initialization for collection with ref type")
      ifElse("_diff != null", {
        `if`("_diff.${fqn2(EntityStorage::extractOneToManyChildren)}<${WorkspaceEntityBase::class.fqn}>(${field.refsConnectionId}, this) == null") {
          line("error(\"Field ${field.owner.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] == null")
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
    is TRef<*> -> {
      ifElse("_diff != null", {
        `if`("_diff.${field.refsConnectionMethodCode("<${WorkspaceEntityBase::class.fqn}>")} == null") {
          line("error(\"Field ${field.owner.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink::class.fqn}(${field.type.getRefType().child}, ${field.refsConnectionId})] == null")
      }.toString()
    }
    is TInt, is TBoolean -> return
    else -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
  }
}

private fun LinesBuilder.isInitializedBaseCode(field: Field<*, *>, expression: String) {
  section("if ($expression)") {
    line("error(\"Field ${field.owner.name}#${field.javaName} should be initialized\")")
  }
}

private fun ValueType<*>.addVirtualFileIndex(field: Field<*, *>): String {
  if (this !is TBlob) return ""
  return when (javaType) {
    "VirtualFileUrl" ->
      """val _diff = diff
|                    if (_diff != null) index(this, "${field.javaName}", value.toHashSet())
        """.trimMargin()
    "LibraryRoot" -> """
                    val _diff = diff
                    if (_diff != null) {
                        indexLibraryRoots(value)
                    }
        """
    else -> ""
  }
}