package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.utils.fqn1
import com.intellij.workspaceModel.codegen.utils.fqn2
import com.intellij.workspaceModel.codegen.writer.*
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*

val ObjProperty<*, *>.implWsEntityFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) {
        append(implWsBlockingCodeOverride)
      }
      else append(implWsBlockingCode)
    } else {
      append("override var $javaName: ${valueType.javaType} = super<${owner.javaFullName}>.$javaName\n")
    }
  }

private val ObjProperty<*, *>.implWsBlockingCode: String
  get() = implWsBlockCode(valueType, name, "")

internal fun ObjProperty<*, *>.implWsBlockCode(fieldType: ValueType<*>, name: String, optionalSuffix: String = ""): String {
  return when (fieldType) {
    ValueType.Int -> "override var $name: ${fieldType.javaType} = 0"
    ValueType.Boolean -> "override var $name: ${fieldType.javaType} = false"
    ValueType.String -> """            
            @JvmField var $implFieldName: String? = null
            override val $name: ${fieldType.javaType}${optionalSuffix}
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
                                
        """.trimIndent()
    is ValueType.ObjRef -> {
      val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
      """
            override val $name: ${fieldType.javaType}$optionalSuffix
                get() = snapshot.${refsConnectionMethodCode()}$notNullAssertion           
                
            """.trimIndent()
    }
    is ValueType.List<*> -> {
      if (fieldType.isRefType()) {
        val connectionName = name.uppercase() + "_CONNECTION_ID"
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
        if ((fieldType.elementType as ValueType.ObjRef<*>).target.openness.extendable) {
          """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = snapshot.${fqn2(EntityStorage::extractOneToAbstractManyChildren)}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
        else {
          """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = snapshot.${fqn2(EntityStorage::extractOneToManyChildren)}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
      }
      else {
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
        """
                @JvmField var $implFieldName: ${fieldType.javaType}? = null
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = $implFieldName$notNullAssertion   
                
                """.trimIndent()
      }
    }
    is ValueType.Set<*> -> {
      if (fieldType.isRefType()) {
        error("Set of references is not supported")
      }
      else {
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
        """
                @JvmField var $implFieldName: ${fieldType.javaType}? = null
                override val $javaName: ${fieldType.javaType}$optionalSuffix
                    get() = $implFieldName$notNullAssertion   
                
                """.trimIndent()
      }
    }
    is ValueType.Map<*, *> -> """
            @JvmField var $implFieldName: ${fieldType.javaType}? = null
            override val $name: ${fieldType.javaType}$optionalSuffix
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
        """.trimIndent()
    is ValueType.Optional<*> -> when (fieldType.type) {
      ValueType.Int, ValueType.Boolean -> "override var $name: ${fieldType.javaType} = null"
      else -> implWsBlockCode(fieldType.type, name, "?")
    }
    is ValueType.JvmClass -> """            
            @JvmField var $implFieldName: ${fieldType.javaClassName}? = null
            override val $name: ${fieldType.javaClassName}$optionalSuffix
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
                                
        """.trimIndent()
    else -> error("Unsupported field type: $this")
  }
}

internal val ObjProperty<*, *>.implWsBlockingCodeOverride: String
  get() {
    val originalField = owner.refsFields.first { it.type.javaType == type.javaType }
    val connectionName = originalField.name.uppercase() + "_CONNECTION_ID"
    var valueType = referencedField.type
    val notNullAssertion = if (valueType is ValueType.Optional<*>) "" else "!!"
    if (valueType is ValueType.Optional<*>) {
      valueType = valueType.type
    }
    val getterName = when (valueType) {
      is ValueType.List<*> -> if (owner.openness.extendable)
        fqn1(EntityStorage::extractOneToAbstractManyParent)
      else
        fqn1(EntityStorage::extractOneToManyParent)
      is ValueType.ObjRef<*> -> if (owner.openness.extendable)
        fqn1(EntityStorage::extractOneToAbstractOneParent)
      else
        fqn1(EntityStorage::extractOneToOneParent)
      else -> error("Unsupported reference type")
    }
    return """
            override val $name: ${type.javaType}
                get() = snapshot.$getterName($connectionName, this)$notNullAssertion
                
        """.trimIndent()
  }

internal val ObjProperty<*, *>.referencedField: ObjProperty<*, *>
  get() {
    val ref = type.getRefType()
    val declaredReferenceFromChild =
      ref.target.refsFields.filter { it.type.getRefType().target == owner && it != this } +
      setOf(ref.target.module, receiver.module).flatMap { it.extensions }.filter { it.type.getRefType().target == owner && it.owner == ref.target && it != this }
    if (declaredReferenceFromChild.isEmpty()) {
      error("Reference should be declared at both entities. It exist at ${owner.name}#$name but absent at ${ref.target.name}")
    }
    if (declaredReferenceFromChild.size > 1) {
      error(
        "More then one reference to ${owner.name} declared at ${declaredReferenceFromChild[0].owner}#${declaredReferenceFromChild[0].name}," +
        "${declaredReferenceFromChild[1].owner}#${declaredReferenceFromChild[1].name}")
    }
    return declaredReferenceFromChild[0]
  }