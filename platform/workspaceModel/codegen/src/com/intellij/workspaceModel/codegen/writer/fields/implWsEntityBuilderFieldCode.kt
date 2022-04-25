// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.ijws.fields

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.impl.*
import deft.storage.codegen.*
import deft.storage.codegen.field.javaMutableType
import deft.storage.codegen.field.javaType
import deft.storage.codegen.field.unsupportedTypeError
import org.jetbrains.deft.codegen.ijws.classes.`else`
import org.jetbrains.deft.codegen.ijws.classes.`for`
import org.jetbrains.deft.codegen.ijws.classes.`if`
import org.jetbrains.deft.codegen.ijws.classes.ifElse
import org.jetbrains.deft.codegen.ijws.getRefType
import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.codegen.utils.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.fields.Field
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

val Field<*, *>.implWsBuilderFieldCode: String
  get() = buildString {
    if (hasSetter) {
      append(implWsBuilderBlockingCode)
    }
    else {
      if (suspendable == true) {
        append("""
                        @Deprecated("Use suspendable getter")                
                        override val $javaName: ${type.javaType}
                            get() = getEntityData().$javaName
                                
            """.trimIndent())
      }
      else {
        append("""                
                        override var $javaName: ${type.javaType}
                            get() = parent
                            set(value) {
                                checkModificationAllowed()
                                parent = value
                            }
                                
            """.trimIndent())
      }
    }

    if (suspendable == true) append("\n").append(builderImplSuspendableCode)
  }

private val Field<*, *>.builderImplSuspendableCode: String
  get() = """
        override suspend fun $suspendableGetterName(): ${type.javaType} = 
                result.$suspendableGetterName()
    """.trimIndent()

private val Field<*, *>.implWsBuilderBlockingCode: String
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
    lines(indent = "    ") {
      line("var ${field.implFieldName}: $javaType? = null")
      sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
        section("get()") {
          line("val _diff = diff")
          line("return if (_diff != null) {")
          line("    _diff.${getterSetterNames.getter}($connectionName, this) ?: ${field.implFieldName}$notNullAssertion")
          line("} else {")
          line("    ${field.implFieldName}$notNullAssertion")
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
            line("this.${field.implFieldName} = value")
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
      val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
      if ((elementType as TRef<*>).targetObjType.abstract) {
        lines(indent = "    ") {
          line("var _${field.javaName}: $javaType? = null")
          sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
            section("get()") {
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("    _diff.${fqn2(WorkspaceEntityStorage::extractOneToAbstractManyChildren)}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (${field.implFieldName} ?: emptyList())")
              line("} else {")
              line("    _${field.javaName}$notNullAssertion")
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
                line("_diff.${fqn5(WorkspaceEntityStorage::updateOneToAbstractManyChildrenOfParent)}($connectionName, this, value.asSequence())")
              }
              `else` {
                backrefListSetup(elementType, field)
                line()
                line("_${field.javaName} = value")
              }
              line("changedProperty.add(\"${field.javaName}\")")
            }
          }
        }
      }
      else {
        lines(indent = "    ") {
          line("var _${field.javaName}: $javaType? = null")
          sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
            section("get()") {
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("    _diff.${fqn2(WorkspaceEntityStorage::extractOneToManyChildren)}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (${field.implFieldName} ?: emptyList())")
              line("} else {")
              line("    _${field.javaName}$notNullAssertion")
              line("}")
            }
            section("set(value)") {
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase::class.fqn}<*> && (item_value as? ${ModifiableWorkspaceEntityBase::class.fqn}<*>)?.diff == null") {
                    line("_diff.addEntity(item_value)")
                  }
                }
                line("_diff.${fqn4(WorkspaceEntityStorage::updateOneToManyChildrenOfParent)}($connectionName, this, value)")
              }
              `else` {
                backrefListSetup(elementType, field)
                line()
                line("_${field.javaName} = value")
                line("// Test")
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
  val javaBuilderType = referencedField.owner.javaImplBuilderName
  val type = referencedField.type
  val isChild = type.getRefType().child
  val extKey = "${ExtRefKey::class.fqn}(\"${field.owner.name}\", \"${field.name}\", $isChild, ${field.refsConnectionId})"
  when (type) {
    is TList<*> -> {
      if (valueType.targetObjType.abstract) {
        `if`("$varName != null") {
          line("val access = $varName::class.${
            fqn(KClass<*>::memberProperties)
          }.single { it.name == \"${referencedField.implFieldName}\" } as ${KMutableProperty1::class.fqn}<*, *>")
          line("access.setter.call($varName, ((access.getter.call($varName) as? List<*>) ?: emptyList<Any>()) + this)")
        }
      }
      else {
        `if`("$varName is $javaBuilderType") {
          if (referencedField !is ExtField<*, *>) {
            line("$varName.${referencedField.implFieldName} = ($varName.${referencedField.implFieldName} ?: emptyList()) + this")
          }
          else {
            line("$varName.extReferences[$extKey] = ($varName.extReferences[$extKey] as? List<Any> ?: emptyList()) + this")
          }
        }
        line("// else you're attaching a new entity to an existing entity that is not modifiable")
      }
    }
    is TOptional<*> -> {
      if (valueType.targetObjType.abstract) {
        `if`("$varName != null") {
          line("val access = $varName::class.${
            fqn(KClass<*>::memberProperties)
          }.single { it.name == \"${referencedField.implFieldName}\" } as ${KMutableProperty1::class.fqn}<*, *>")
          line("// x")
          line("access.setter.call($varName, this)")
        }
      }
      else {
        `if`("$varName is $javaBuilderType") {
          if (referencedField !is ExtField<*, *>) {
            line("$varName.${referencedField.implFieldName} = this")
          }
          else {
            line("$varName.extReferences[$extKey] = this")
          }
        }
        line("// else you're attaching a new entity to an existing entity that is not modifiable")
      }
    }
    is TRef<*> -> {
      if (valueType.targetObjType.abstract) {
        `if`("$varName != null") {
          line("val access = $varName::class.${
            fqn(KClass<*>::memberProperties)
          }.single { it.name == \"${referencedField.implFieldName}\" } as ${KMutableProperty1::class.fqn}<*, *>")
          line("access.setter.call($varName, this)")
        }
      }
      else {
        `if`("$varName is $javaBuilderType") {
          if (referencedField !is ExtField<*, *>) {
            line("$varName.${referencedField.implFieldName} = this")
          }
          else {
            line("$varName.extReferences[$extKey] = this")
          }
        }
        line("// else you're attaching a new entity to an existing entity that is not modifiable")
      }
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
    is TList<*> -> if (field.type.isRefType()) {
      ifElse("_diff != null", {
        `if`("_diff.${fqn2(WorkspaceEntityStorage::extractOneToManyChildren)}<${WorkspaceEntityBase::class.fqn}>(${field.refsConnectionId}, this) == null") {
          line("error(\"Field ${field.owner.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "_$javaName == null")
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
        isInitializedBaseCode(field, "_$javaName == null")
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
                        val jarDirectories = mutableSetOf<VirtualFileUrl>()
                        val libraryRootList = value.map {
                            if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
                                jarDirectories.add(it.url)
                            }
                            it.url
                        }.toHashSet()
                        index(this, "${field.javaName}", libraryRootList)
                        indexJarDirectories(this, jarDirectories)
                    }
        """
    else -> ""
  }
}