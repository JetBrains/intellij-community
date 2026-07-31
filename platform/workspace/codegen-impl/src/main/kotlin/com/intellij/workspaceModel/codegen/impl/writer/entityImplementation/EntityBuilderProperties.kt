// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.dsl.unsupportedTypeError
import com.intellij.workspaceModel.codegen.impl.writer.EntityLink
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.LibraryRoot
import com.intellij.workspaceModel.codegen.impl.writer.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.writer.MutableEntityStorageInstrumentation
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceList
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceSet
import com.intellij.workspaceModel.codegen.impl.writer.QualifiedName
import com.intellij.workspaceModel.codegen.impl.writer.SdkRoot
import com.intellij.workspaceModel.codegen.impl.writer.VirtualFileUrl
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdReferenceCode

fun CodeContext.getImplWsBuilderFieldCode(receiver: ObjClass<*>, property: ObjProperty<*, *>) {
  implWsBuilderBlockingCode(receiver, property.valueType, property)
}

private fun CodeContext.implWsBuilderBlockingCode(
  receiver: ObjClass<*>,
  valueType: ValueType<*>,
  objProperty: ObjProperty<*, *>,
  optionalSuffix: String = "",
) {
  when (valueType) {
    ValueType.Boolean, ValueType.Int, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double, ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> {
      +"override var ${objProperty.javaName}: ${getJavaMutableType(objProperty)}$optionalSuffix"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    // TODO: why String is separate from the above? What about optionalSuffix?
    ValueType.String -> {
      +"override var ${objProperty.javaName}: ${getJavaMutableType(objProperty)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    is ValueType.ObjRef -> {
      val connectionName = connectionIdForReference(objProperty)
      val getterSetterNames = refNames(objProperty)
      val unwrappedType = unwrapReferenceType(valueType)
      if (getterSetterNames == null || unwrappedType == null) {
        notReferenceError("implWsBuilderBlockingCode", objProperty)
        return
      }

      // Opposite field may be either one-to-one or one-to-many
      val notNullAssertion =
        if (optionalSuffix.isBlank()) " ?: error(\"${objProperty.name} is null for ${objProperty.receiver.name}\")" else ""
      sectionNoBrackets("override var ${objProperty.javaName}: ${getJavaBuilderTypeWithGeneric(objProperty, valueType)}$optionalSuffix") {
        section("get()") {
          line("val _diff = diff")
          line("return if (_diff != null) {")
          line("((_diff as $MutableEntityStorageInstrumentation).${getterSetterNames.getterBuilder}($connectionName, this) as? ${
            getJavaBuilderTypeWithGeneric(objProperty,
                                          valueType)
          }) ?: (this.entityLinks[${EntityLink}(${unwrappedType.child}, ${
            connectionIdForReference(
              objProperty)
          })] as? ${getJavaBuilderTypeWithGeneric(objProperty, valueType)})$notNullAssertion")
          line("} else {")
          line("(this.entityLinks[${EntityLink}(${unwrappedType.child}, ${connectionIdForReference(objProperty)})] as? ${
            getJavaBuilderTypeWithGeneric(objProperty,
                                          valueType)
          })$notNullAssertion")
          line("}")
        }
        section("set(value)") {
          line("checkModificationAllowed()")
          line("val _diff = diff")
          `if`("_diff != null && value is ${ModifiableWorkspaceEntityBase}<*, *> && value.diff == null") {
            backrefSetup(objProperty, checkedForModifiable = true)
            suppressUncheckedCast()
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
            backrefSetup(objProperty)
            line("this.entityLinks[${EntityLink}(${unwrappedType.child}, ${connectionIdForReference(objProperty)})] = value")
          }
          line("changedProperty.add(\"${objProperty.javaName}\")")
          symbolicIdReferenceCode(receiver, objProperty)
        }
      }

    }

    is ValueType.List<*> -> {
      val elementType = valueType.elementType
      if (elementType.isReferenceType()) {
        val isChild = unwrapReferenceType(objProperty.valueType)?.child
        if (isChild == null) {
          notReferenceError("list property", objProperty)
          return
        }
        val connectionName = connectionIdForReference(objProperty)
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!"
        else {
          reportPropertyError("Nullable reference lists are prohibited", objProperty)
          return
        }
        if ((elementType as ValueType.ObjRef<*>).target.openness.extendable) {
          sectionNoBrackets("override var ${objProperty.javaName}: ${
            getJavaBuilderTypeWithGeneric(objProperty, valueType)
          }$optionalSuffix") {
            section("get()") {
              line("val _diff = diff")
              line("return if (_diff != null) {")
              line("((_diff as $MutableEntityStorageInstrumentation).getManyChildrenBuilders($connectionName, this)$notNullAssertion.toList() as ${
                getJavaBuilderTypeWithGeneric(objProperty,
                                              valueType)
              }) + (this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(objProperty)})] as? ${
                getJavaBuilderTypeWithGeneric(objProperty,
                                              valueType)
              } ?: emptyList())")
              line("} else {")
              suppressUncheckedCast()
              line("this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(objProperty)})] as ${
                getJavaBuilderTypeWithGeneric(objProperty,
                                              valueType)
              } ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
              line("}")
            }
            section("set(value)") {
              lineComment("Set list of ref types for abstract entities")
              line("checkModificationAllowed()")
              line("val _diff = diff")
              `if`("_diff != null") {
                `for`("item_value in value") {
                  `if`("item_value is ${ModifiableWorkspaceEntityBase}<*, *> && (item_value as? ${ModifiableWorkspaceEntityBase}<*, *>)?.diff == null") {
                    lineComment("Backref setup before adding to store an abstract entity")
                    backrefSetup(objProperty, "item_value", true)
                    suppressUncheckedCast()
                    line("_diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)")
                  }
                }
                line("_diff.${Instrumentation.replaceChildren}($connectionName, this, value)")
              }
              `else` {
                backrefListSetup(objProperty)
                line("this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(objProperty)})] = value")
              }
              line("changedProperty.add(\"${objProperty.javaName}\")")
            }
          }

        }
        else {
          lineComment("List of non-abstract referenced types")
          sectionNoBrackets("override var ${objProperty.javaName}: ${
            getJavaBuilderTypeWithGeneric(objProperty,
                                          valueType)
          }$optionalSuffix") {
            section("get()") {
              lineComment("Getter of the list of non-abstract referenced types")
              line("val _diff = diff")
              line("return if (_diff != null) {")
              suppressUncheckedCast()
              line("((_diff as $MutableEntityStorageInstrumentation).getManyChildrenBuilders($connectionName, this).toList() as ${
                getJavaBuilderTypeWithGeneric(objProperty,
                                              valueType)
              }) + (this.entityLinks[${EntityLink}($isChild, ${
                connectionIdForReference(
                  objProperty)
              })] as? ${getJavaBuilderTypeWithGeneric(objProperty, valueType)} ?: emptyList())")
              line("} else {")
              suppressUncheckedCast()
              line("this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(objProperty)})] as? ${
                getJavaBuilderTypeWithGeneric(objProperty,
                                              valueType)
              } ${if (notNullAssertion.isNotBlank()) "?: emptyList()" else ""}")
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
                    backrefSetup(objProperty, "item_value", true)
                    suppressUncheckedCast()
                    line("_diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)")
                  }
                }
                line("_diff.${Instrumentation.replaceChildren}($connectionName, this, value)")
              }
              `else` {
                backrefListSetup(objProperty)
                line("this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(objProperty)})] = value")
              }
              line("changedProperty.add(\"${objProperty.javaName}\")")
            }
          }

        }
      }
      else {
        +"private val ${objProperty.javaName}Updater: (value: List<${getJavaType(objProperty, elementType)}>) -> Unit = { value ->"
        +elementType.addVirtualFileIndex(objProperty)
        +"changedProperty.add(\"${objProperty.javaName}\")"
        +"}"
        +"override var ${objProperty.javaName}: MutableList<${getJavaType(objProperty, elementType)}>"
        section("get()") {
          +"val collection_${objProperty.javaName} = getEntityData().${objProperty.javaName}"
          +"if (collection_${objProperty.javaName} !is ${MutableWorkspaceList}) return collection_${objProperty.javaName}"
          +"if (diff == null || modifiable.get()) {"
          +"collection_${objProperty.javaName}.setModificationUpdateAction(${objProperty.javaName}Updater)"
          +"} else {"
          +"collection_${objProperty.javaName}.cleanModificationUpdateAction()"
          +"}"
          +"return collection_${objProperty.javaName}"
        }
        section("set(value)") {
          +"checkModificationAllowed()"
          +"getEntityData(true).${objProperty.javaName} = value"
          +"${objProperty.javaName}Updater.invoke(value)"
        }
      }
    }

    // TODO: suspicious that code for List and Set is different
    is ValueType.Set<*> -> {
      val elementType = valueType.elementType
      if (valueType.isReferenceType()) {
        reportPropertyError("Set of references is not supported", objProperty)
        return
      }
      else {
        +"private val ${objProperty.javaName}Updater: (value: Set<${getJavaType(objProperty, elementType)}>) -> Unit = { value ->"
        +elementType.addVirtualFileIndex(objProperty)
        +"changedProperty.add(\"${objProperty.javaName}\")"
        +"}"
        +"override var ${objProperty.javaName}: MutableSet<${getJavaType(objProperty, elementType)}>"
        section("get()") {
          +"val collection_${objProperty.javaName} = getEntityData().${objProperty.javaName}"
          +"if (collection_${objProperty.javaName} !is ${MutableWorkspaceSet}) return collection_${objProperty.javaName}"
          +"if (diff == null || modifiable.get()) {"
          +"collection_${objProperty.javaName}.setModificationUpdateAction(${objProperty.javaName}Updater)"
          +"} else {"
          +"collection_${objProperty.javaName}.cleanModificationUpdateAction()"
          +"}"
          +"return collection_${objProperty.javaName}"
        }
        section("set(value)") {
          +"checkModificationAllowed()"
          +"getEntityData(true).${objProperty.javaName} = value"
          +"${objProperty.javaName}Updater.invoke(value)"
        }
      }
    }

    is ValueType.Map<*, *> -> {
      +"override var ${objProperty.javaName}: ${getJavaType(objProperty, valueType)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    is ValueType.Optional<*> -> implWsBuilderBlockingCode(receiver, valueType.type, objProperty, "?")
    is ValueType.Structure<*> -> +"//TODO: ${objProperty.javaName}"
    is ValueType.JvmClass -> {
      +"override var ${objProperty.javaName}: ${getJavaType(objProperty, valueType).appendSuffix(optionalSuffix)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
        if (getJavaType(objProperty, valueType).decoded == VirtualFileUrl.decoded) {
          +"val _diff = diff"
          +"if (_diff != null) index(this, \"${objProperty.javaName}\", value)"
        }
      }
    }

    else -> {
      unsupportedTypeError(valueType, objProperty)
    }
  }
}

private fun CodeContext.backrefSetup(
  field: ObjProperty<*, *>,
  varName: String = "value",
  checkedForModifiable: Boolean = false,
) {
  val referencedField = getReferencedField(field) ?: return
  val type = referencedField.valueType
  val isChild = unwrapReferenceType(type)?.child
  if (isChild == null) {
    notReferenceError("backref", field)
    return
  }
  when (type) {
    is ValueType.List<*> -> {
      lineComment("Setting backref of the list")
      ifif(!checkedForModifiable, "$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        suppressUncheckedCast()
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] as? List<Any> ?: emptyList()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] = data")
      }
      // else you're attaching a new entity to an existing entity that is not modifiable
    }

    // TODO: sets are not supported ?
    is ValueType.Set<*> -> {
      lineComment("Setting backref of the set")
      ifif(!checkedForModifiable, "$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("val data = ($varName.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] as? Set<Any> ?: emptySet()) + this")
        line("$varName.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] = data")
      }
      // else you're attaching a new entity to an existing entity that is not modifiable
    }

    is ValueType.Optional<*>, is ValueType.ObjRef<*> -> {
      ifif(!checkedForModifiable, "$varName is ${ModifiableWorkspaceEntityBase}<*, *>") {
        line("$varName.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] = this")
      }
      // else you're attaching a new entity to an existing entity that is not modifiable
    }

    else -> {
      reportPropertyError("Expected $type to be an entity reference", referencedField)
    }
  }
}

private fun CodeContext.backrefListSetup(
  field: ObjProperty<*, *>,
  varName: String = "value",
) {
  val itemName = "item_$varName"
  `for`("$itemName in $varName") {
    backrefSetup(field, itemName)
  }
}

fun CodeContext.implWsBuilderIsInitializedCode(field: ObjProperty<*, *>) {
  val javaName = field.javaName
  val isChild = unwrapReferenceType(field.valueType)?.child
  when (field.valueType) {
    is ValueType.List<*> -> if (field.valueType.isReferenceType()) {
      if (isChild == null) {
        notReferenceError("isInitialized", field)
        return
      }
      lineComment("Check initialization for list with ref type")
      ifElse("_diff != null", {
        `if`("_diff.${Instrumentation.getManyChildrenBuilders}(${connectionIdForReference(field)}, this) == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] == null")
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }

    is ValueType.ObjRef<*> -> {
      if (isChild == null) {
        notReferenceError("isInitialized", field)
        return
      }
      ifElse("_diff != null", {
        `if`("_diff.${refsConnectionMethodCode(field, true)} == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] == null")
      }.toString()
    }

    is ValueType.Int, is ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong,
      -> return
    else -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
  }
}

private fun CodeContext.isInitializedBaseCode(field: ObjProperty<*, *>, expression: String) {
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

data class RefMethods(
  val getter: QualifiedName,
  val getterBuilder: String,
  val setter: QualifiedName,
  val many: Boolean = false,
)

fun GeneratorContext.refNames(objProperty: ObjProperty<*, *>): RefMethods? {
  if (!objProperty.valueType.isReferenceType()) return null
  return when (objProperty.valueType) {
    is ValueType.ObjRef -> constructCode(objProperty, objProperty.valueType)
    is ValueType.Optional -> constructCode(objProperty, (objProperty.valueType as ValueType.Optional<*>).type)
    is ValueType.List<*> -> RefMethods(Instrumentation.getManyChildrenBuilders,
                                       "getManyChildrenBuilders",
                                       Instrumentation.replaceChildren,
                                       true)
    else -> null
  }
}

private fun GeneratorContext.constructCode(objProperty: ObjProperty<*, *>, type: ValueType<*>): RefMethods? {
  type as ValueType.ObjRef<*>

  return if (type.child) {
    RefMethods(Instrumentation.getOneChild, "getOneChildBuilder", Instrumentation.replaceChildren, true)
  }
  else {
    val valueType = getReferencedField(objProperty)?.valueType?.let { if (it is ValueType.Optional<*>) it.type else it }
    if (valueType == null) {
      notReferenceError("constructCode", objProperty)
      return null
    }
    if (valueType !is ValueType.List<*> && valueType !is ValueType.ObjRef<*>) {
      reportPropertyError("Unsupported reference type", objProperty)
      return null
    }
    RefMethods(Instrumentation.getParent, "getParentBuilder", Instrumentation.addChild)
  }
}

fun CodeContext.suppressUncheckedCast() {
  annotation("Suppress(\"UNCHECKED_CAST\")")
}

private fun CodeContext.ifif(conditionForCondition: Boolean, conditionCode: String, blockCode: CodeContext.() -> Unit) {
  if (conditionForCondition) {
    `if`(conditionCode) {
      blockCode()
    }
  }
  else {
    blockCode()
  }
}