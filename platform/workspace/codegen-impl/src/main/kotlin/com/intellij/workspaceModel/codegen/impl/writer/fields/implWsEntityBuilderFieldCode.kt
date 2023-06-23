// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.impl.writer.classes.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.javaName

val ObjProperty<*, *>.implWsBuilderFieldCode: String
  get() = valueType.implWsBuilderBlockingCode(this)

private fun ValueType<*>.implWsBuilderBlockingCode(field: ObjProperty<*, *>, optionalSuffix: String = ""): String = when (this) {
  ValueType.Boolean, ValueType.Int -> """
            override var ${field.javaName}: ${field.valueType.javaMutableType}$optionalSuffix
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
        """.trimIndent()
  ValueType.String -> """
            override var ${field.javaName}: ${field.valueType.javaMutableType}
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
        """.trimIndent()
  is ValueType.ObjRef -> {
    val connectionName = field.refsConnectionId
    val getterSetterNames = field.refNames()

    // Opposite field may be either one-to-one or one-to-many

    val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
    lines {
      sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
        section("get()") {
          line("val _diff = diff")
          line("return if (_diff != null) {")
          line("    _diff.${getterSetterNames.getter}($connectionName, this) ?: this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})]$notNullAssertion as$optionalSuffix $javaType")
          line("} else {")
          line("    this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})]$notNullAssertion as$optionalSuffix $javaType")
          line("}")
        }
        section("set(value)") {
          line("checkModificationAllowed()")
          line("val _diff = diff")
          `if`("_diff != null && value is ${ModifiableWorkspaceEntityBase}<*, *> && value.diff == null") {
            backrefSetup(field)
            line("_diff.addEntity(value)")
          }
          section("if (_diff != null && (value !is ${ModifiableWorkspaceEntityBase}<*, *> || value.diff != null))") {
            line("_diff.${getterSetterNames.setter}($connectionName, this, value)")
          }
          section("else") {
            backrefSetup(field)
            line()
            line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] = value")
          }
          line("changedProperty.add(\"${field.javaName}\")")
        }
      }
    }
  }
  is ValueType.List<*> -> {
    val elementType = this.elementType
    if (this.isRefType()) {
      val connectionName = field.refsConnectionId
      val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else error("It's prohibited to have nullable reference list")
      if ((elementType as ValueType.ObjRef<*>).target.openness.extendable) {
        lines(level = 1) {
          sectionNoBrackets("override var ${field.javaName}: $javaType$optionalSuffix") {
            section("get()") {
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("    _diff.${EntityStorage.extractOneToAbstractManyChildren}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] as? $javaType ?: emptyList())")
              line("} else {")
              line("    this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] as $javaType ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
              line("}")
            }
            section("set(value)") {
              lineComment("Set list of ref types for abstract entities")
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase}<*, *> && (item_value as? ${
                    ModifiableWorkspaceEntityBase
                  }<*, *>)?.diff == null") {
                    lineComment("Backref setup before adding to store an abstract entity")
                    backrefSetup(field, "item_value")
                    line("_diff.addEntity(item_value)")
                  }
                }
                line("_diff.${EntityStorage.updateOneToAbstractManyChildrenOfParent}($connectionName, this, value.asSequence())")
              }
              `else` {
                backrefListSetup(field)
                line()
                line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] = value")
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
              line("    _diff.${EntityStorage.extractOneToManyChildren}<${elementType.javaType}>($connectionName, this)$notNullAssertion.toList() + (this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] as? $javaType ?: emptyList())")
              line("} else {")
              line(
                "    this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] as? $javaType ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
              line("}")
            }
            section("set(value)") {
              lineComment("Setter of the list of non-abstract referenced types")
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase}<*, *> && (item_value as? ${ModifiableWorkspaceEntityBase}<*, *>)?.diff == null") {
                    lineComment("Backref setup before adding to store")
                    backrefSetup(field, "item_value")
                    line()
                    line("_diff.addEntity(item_value)")
                  }
                }
                line("_diff.${EntityStorage.updateOneToManyChildrenOfParent}($connectionName, this, value)")
              }
              `else` {
                backrefListSetup(field)
                line()
                line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] = value")
              }
              line("changedProperty.add(\"${field.javaName}\")")
            }
          }
        }
      }
    }
    else {
      """
            private val ${field.javaName}Updater: (value: List<${elementType.javaType}>) -> Unit = { value ->
                ${elementType.addVirtualFileIndex(field)}
                changedProperty.add("${field.javaName}")
            }
            override var ${field.javaName}: MutableList<${elementType.javaType}>
                get() {
                    val collection_${field.javaName} = getEntityData().${field.javaName}
                    if (collection_${field.javaName} !is ${MutableWorkspaceList}) return collection_${field.javaName}
                    if (diff == null || modifiable.get()) {
                      collection_${field.javaName}.setModificationUpdateAction(${field.javaName}Updater)
                    } else {
                      collection_${field.javaName}.cleanModificationUpdateAction()
                    }
                    return collection_${field.javaName}  
                }
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    ${field.javaName}Updater.invoke(value)
                }
                
            """.trimIndent()
    }
  }
  is ValueType.Set<*> -> {
    val elementType = this.elementType
    if (this.isRefType()) {
      error("Set of references is not supported")
    } else {
      """
            private val ${field.javaName}Updater: (value: Set<${elementType.javaType}>) -> Unit = { value ->
                ${elementType.addVirtualFileIndex(field)}
                changedProperty.add("${field.javaName}")
            }
            override var ${field.javaName}: MutableSet<${elementType.javaType}>
                get() { 
                    val collection_${field.javaName} = getEntityData().${field.javaName}
                    if (collection_${field.javaName} !is ${MutableWorkspaceSet}) return collection_${field.javaName}
                    if (diff == null || modifiable.get()) {
                      collection_${field.javaName}.setModificationUpdateAction(${field.javaName}Updater)
                    } else {
                      collection_${field.javaName}.cleanModificationUpdateAction()
                    }
                    return collection_${field.javaName} 
                }
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    ${field.javaName}Updater.invoke(value)
                }
                
            """.trimIndent()
    }
  }
  is ValueType.Map<*, *> -> """
            override var ${field.javaName}: $javaType
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                }
                
    """.trimIndent()
  is ValueType.Optional<*> -> type.implWsBuilderBlockingCode(field, "?")
  is ValueType.Structure<*> -> "//TODO: ${field.javaName}"
  is ValueType.JvmClass -> """
            override var ${field.javaName}: ${javaType.appendSuffix(optionalSuffix)}
                get() = getEntityData().${field.javaName}
                set(value) {
                    checkModificationAllowed()
                    getEntityData(true).${field.javaName} = value
                    changedProperty.add("${field.javaName}")
                    ${
    if (javaType.decoded == VirtualFileUrl.decoded)
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
  field: ObjProperty<*, *>,
  varName: String = "value",
) {
  val referencedField = field.referencedField
  val type = referencedField.valueType
  val isChild = type.getRefType().child
  when (type) {
    is ValueType.List<*> -> {
      lineComment("Setting backref of the list")
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] as? List<Any> ?: emptyList()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is ValueType.Set<*> -> {
      lineComment("Setting backref of the set")
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] as? Set<Any> ?: emptySet()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is ValueType.Optional<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("$varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] = this")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    is ValueType.ObjRef<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("$varName.entityLinks[${EntityLink}($isChild, ${field.refsConnectionId})] = this")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }
    else -> error("Unexpected")
  }
}

private fun LinesBuilder.backrefListSetup(
  field: ObjProperty<*, *>,
  varName: String = "value",
) {
  val itemName = "item_$varName"
  `for`("$itemName in $varName") {
    backrefSetup(field, itemName)
  }
}

fun LinesBuilder.implWsBuilderIsInitializedCode(field: ObjProperty<*, *>) {
  val javaName = field.javaName
  when (field.valueType) {
    is ValueType.List<*> -> if (field.valueType.isRefType()) {
      lineComment("Check initialization for list with ref type")
      ifElse("_diff != null", {
        `if`("_diff.${EntityStorage.extractOneToManyChildren}<${WorkspaceEntityBase}>(${field.refsConnectionId}, this) == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] == null")
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
    is ValueType.ObjRef<*> -> {
      ifElse("_diff != null", {
        `if`("_diff.${field.refsConnectionMethodCode("<${WorkspaceEntityBase}>")} == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${field.refsConnectionId})] == null")
      }.toString()
    }
    is ValueType.Int, is ValueType.Boolean -> return
    else -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
  }
}

private fun LinesBuilder.isInitializedBaseCode(field: ObjProperty<*, *>, expression: String) {
  section("if ($expression)") {
    line("error(\"Field ${field.receiver.name}#${field.javaName} should be initialized\")")
  }
}

private fun ValueType<*>.addVirtualFileIndex(field: ObjProperty<*, *>): String {
  return when {
    this is ValueType.Blob && javaClassName == VirtualFileUrl.decoded ->
      """val _diff = diff
|                    if (_diff != null) index(this, "${field.javaName}", value)
        """.trimMargin()
    this is ValueType.JvmClass && javaClassName == LibraryRoot.decoded -> """
                    val _diff = diff
                    if (_diff != null) {
                        indexLibraryRoots(value)
                    }
        """
    else -> ""
  }
}