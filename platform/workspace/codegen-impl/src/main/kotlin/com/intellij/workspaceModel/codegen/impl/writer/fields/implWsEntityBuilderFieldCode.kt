// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.EntityLink
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.LibraryRoot
import com.intellij.workspaceModel.codegen.impl.writer.LinesBuilder
import com.intellij.workspaceModel.codegen.impl.writer.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.writer.MutableEntityStorageInstrumentation
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceList
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceSet
import com.intellij.workspaceModel.codegen.impl.writer.SdkRoot
import com.intellij.workspaceModel.codegen.impl.writer.VirtualFileUrl
import com.intellij.workspaceModel.codegen.impl.writer.classes.`else`
import com.intellij.workspaceModel.codegen.impl.writer.classes.`for`
import com.intellij.workspaceModel.codegen.impl.writer.classes.`if`
import com.intellij.workspaceModel.codegen.impl.writer.classes.ifElse
import com.intellij.workspaceModel.codegen.impl.writer.classes.lineComment
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.lines
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdReferenceCode

fun getImplWsBuilderFieldCode(receiver: ObjClass<*>, property: ObjProperty<*, *>): String {
  return implWsBuilderBlockingCode(receiver, property.valueType, property)
}

private fun implWsBuilderBlockingCode(receiver: ObjClass<*>, valueType: ValueType<*>, field: ObjProperty<*, *>, optionalSuffix: String = ""): String {
  return when (valueType) {
    ValueType.Boolean, ValueType.Int, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double, ValueType.Short,
    ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong,
      -> """
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
      val connectionName = getRefsConnectionId(field)
      val getterSetterNames = field.refNames()

      // Opposite field may be either one-to-one or one-to-many
      val notNullAssertion = if (optionalSuffix.isBlank()) " ?: error(\"${field.name} is null for ${field.receiver.name}\")" else ""
      lines {
        sectionNoBrackets("override var ${field.javaName}: ${valueType.javaBuilderTypeWithGeneric}$optionalSuffix") {
          section("get()") {
            line("val _diff = diff")
            line("return if (_diff != null) {")
            line("((_diff as $MutableEntityStorageInstrumentation).${getterSetterNames.getterBuilder}($connectionName, this) as? ${valueType.javaBuilderTypeWithGeneric}) ?: (this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(
              field)})] as? ${valueType.javaBuilderTypeWithGeneric})$notNullAssertion")
            line("} else {")
            line("(this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] as? ${valueType.javaBuilderTypeWithGeneric})$notNullAssertion")
            line("}")
          }
          section("set(value)") {
            line("checkModificationAllowed()")
            line("val _diff = diff")
            `if`("_diff != null && value is ${ModifiableWorkspaceEntityBase}<*, *> && value.diff == null") {
              backrefSetup(field)
              line("_diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)")
            }
            section("if (_diff != null && (value !is ${ModifiableWorkspaceEntityBase}<*, *> || value.diff != null))") {
              if (valueType.child) {
                line("_diff.${Instrumentation.replaceChildren}($connectionName, this, listOfNotNull(value))")
              }
              else {
                line("_diff.${Instrumentation.addChild}($connectionName, value, this)")
              }
            }
            section("else") {
              backrefSetup(field)
              line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] = value")
            }
            line("changedProperty.add(\"${field.javaName}\")")
            symbolicIdReferenceCode(receiver, field)
          }
        }
      }
    }

    is ValueType.List<*> -> {
      val elementType = valueType.elementType
      if (valueType.isRefType()) {
        val connectionName = getRefsConnectionId(field)
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else error("It's prohibited to have nullable reference list")
        if ((elementType as ValueType.ObjRef<*>).target.openness.extendable) {
          lines {
            sectionNoBrackets("override var ${field.javaName}: ${valueType.javaBuilderTypeWithGeneric}$optionalSuffix") {
              section("get()") {
                line("val _diff = diff")
                line("return if (_diff != null) {")
                line("((_diff as $MutableEntityStorageInstrumentation).getManyChildrenBuilders($connectionName, this)$notNullAssertion.toList() as ${valueType.javaBuilderTypeWithGeneric}) + (this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(
                  field)})] as? ${valueType.javaBuilderTypeWithGeneric} ?: emptyList())")
                line("} else {")
                line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] as ${valueType.javaBuilderTypeWithGeneric} ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
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
                      line("_diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)")
                    }
                  }
                  line("_diff.${Instrumentation.replaceChildren}($connectionName, this, value)")
                }
                `else` {
                  backrefListSetup(field)
                  line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] = value")
                }
                line("changedProperty.add(\"${field.javaName}\")")
              }
            }
          }
        }
        else {
          lines {
            lineComment("List of non-abstract referenced types")
            line("var _${field.javaName}: ${valueType.javaType}? = emptyList()")
            sectionNoBrackets("override var ${field.javaName}: ${valueType.javaBuilderTypeWithGeneric}$optionalSuffix") {
              section("get()") {
                lineComment("Getter of the list of non-abstract referenced types")
                line("val _diff = diff")
                line("return if (_diff != null) {")
                line("((_diff as $MutableEntityStorageInstrumentation).getManyChildrenBuilders($connectionName, this)$notNullAssertion.toList() as ${valueType.javaBuilderTypeWithGeneric}) + (this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(
                  field)})] as? ${valueType.javaBuilderTypeWithGeneric} ?: emptyList())")
                line("} else {")
                line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] as? ${valueType.javaBuilderTypeWithGeneric} ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
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
                      line("_diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)")
                    }
                  }
                  line("_diff.${Instrumentation.replaceChildren}($connectionName, this, value)")
                }
                `else` {
                  backrefListSetup(field)
                  line("this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] = value")
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
      val elementType = valueType.elementType
      if (valueType.isRefType()) {
        error("Set of references is not supported")
      }
      else {
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
    override var ${field.javaName}: ${valueType.javaType}
    get() = getEntityData().${field.javaName}
    set(value) {
    checkModificationAllowed()
    getEntityData(true).${field.javaName} = value
    changedProperty.add("${field.javaName}")
    }
    """.trimIndent()

    is ValueType.Optional<*> -> implWsBuilderBlockingCode(receiver, valueType.type, field, "?")
    is ValueType.Structure<*> -> "//TODO: ${field.javaName}"
    is ValueType.JvmClass -> """
    override var ${field.javaName}: ${valueType.javaType.appendSuffix(optionalSuffix)}
    get() = getEntityData().${field.javaName}
    set(value) {
    checkModificationAllowed()
    getEntityData(true).${field.javaName} = value
    changedProperty.add("${field.javaName}")
    ${
      if (valueType.javaType.decoded == VirtualFileUrl.decoded)
        """
        val _diff = diff
        if (_diff != null) index(this, "${field.javaName}", value)
        """.trimIndent()
      else ""
    }
    }
    """.trimIndent()

    else -> valueType.unsupportedTypeError()
  }
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
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] as? List<Any> ?: emptyList()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }

    is ValueType.Set<*> -> {
      lineComment("Setting backref of the set")
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] as? Set<Any> ?: emptySet()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] = data")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }

    is ValueType.Optional<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("$varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] = this")
      }
      line("// else you're attaching a new entity to an existing entity that is not modifiable")
    }

    is ValueType.ObjRef<*> -> {
      `if`("$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("$varName.entityLinks[${EntityLink}($isChild, ${getRefsConnectionId(field)})] = this")
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
        `if`("_diff.${Instrumentation.getManyChildrenBuilders}(${getRefsConnectionId(field)}, this) == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] == null")
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }

    is ValueType.ObjRef<*> -> {
      ifElse("_diff != null", {
        `if`("_diff.${field.refsConnectionMethodCode(true)} == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}(${field.valueType.getRefType().child}, ${getRefsConnectionId(field)})] == null")
      }.toString()
    }

    is ValueType.Int, is ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> return
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
    this is ValueType.Blob && kotlinClassName == VirtualFileUrl.decoded ->
      """
        val _diff = diff
        if (_diff != null) index(this, "${field.javaName}", value)
        """.trimIndent()

    this is ValueType.JvmClass && kotlinClassName == LibraryRoot.decoded -> """
      val _diff = diff
      if (_diff != null) {
      indexLibraryRoots(value)
      }
      """.trimIndent()

    this is ValueType.JvmClass && javaClassName == SdkRoot.decoded -> """
      val _diff = diff
      if (_diff != null) {
      indexSdkRoots(value)
      }
      """.trimIndent()

    else -> ""
  }
}