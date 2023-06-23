package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.EntityStorage

val ObjProperty<*, *>.implWsEntityFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) {
        append(implWsBlockingCodeOverride)
      }
      else append(implWsBlockingCode)
    } else {
      append("override var $javaName: ${valueType.javaType} = dataSource.$javaName\n")
    }
  }

private val ObjProperty<*, *>.implWsBlockingCode: String
  get() = implWsBlockCode(valueType, name, "")

internal fun ObjProperty<*, *>.implWsBlockCode(fieldType: ValueType<*>, name: String, optionalSuffix: String = ""): String {
  return when (fieldType) {
    ValueType.Int -> "override val $name: ${fieldType.javaType} get() = dataSource.$name"
    ValueType.Boolean -> "override val $name: ${fieldType.javaType} get() = dataSource.$name"
    ValueType.String -> """            
            override val $name: ${fieldType.javaType}${optionalSuffix}
                get() = dataSource.$name
                                
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
                    get() = snapshot.${EntityStorage.extractOneToAbstractManyChildren}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
        else {
          """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = snapshot.${EntityStorage.extractOneToManyChildren}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
      }
      else {
        """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = dataSource.$name
                
                """.trimIndent()
      }
    }
    is ValueType.Set<*> -> {
      if (fieldType.isRefType()) {
        error("Set of references is not supported")
      }
      else {
        """
                override val $javaName: ${fieldType.javaType}$optionalSuffix
                    get() = dataSource.$name
                
                """.trimIndent()
      }
    }
    is ValueType.Map<*, *> -> """
            override val $name: ${fieldType.javaType}$optionalSuffix
                get() = dataSource.$name
        """.trimIndent()
    is ValueType.Optional<*> -> when (fieldType.type) {
      ValueType.Int, ValueType.Boolean -> "override val $name: ${fieldType.javaType} get() = dataSource.$name"
      else -> implWsBlockCode(fieldType.type, name, "?")
    }
    is ValueType.JvmClass -> """            
            override val $name: ${fieldType.javaClassName.toQualifiedName()}$optionalSuffix
                get() = dataSource.$name
                                
        """.trimIndent()
    else -> error("Unsupported field type: $this")
  }
}

internal val ObjProperty<*, *>.implWsBlockingCodeOverride: String
  get() {
    val originalField = receiver.refsFields.first { it.valueType.javaType == valueType.javaType }
    val connectionName = originalField.name.uppercase() + "_CONNECTION_ID"
    var valueType = referencedField.valueType
    val notNullAssertion = if (valueType is ValueType.Optional<*>) "" else "!!"
    if (valueType is ValueType.Optional<*>) {
      valueType = valueType.type
    }
    val getterName = when (valueType) {
      is ValueType.List<*> -> if (receiver.openness.extendable)
        EntityStorage.extractOneToAbstractManyParent
      else
        EntityStorage.extractOneToManyParent
      is ValueType.ObjRef<*> -> if (receiver.openness.extendable)
        EntityStorage.extractOneToAbstractOneParent
      else
        EntityStorage.extractOneToOneParent
      else -> error("Unsupported reference type")
    }
    return """
            override val $name: ${this.valueType.javaType}
                get() = snapshot.$getterName($connectionName, this)$notNullAssertion
                
        """.trimIndent()
  }

internal val ObjProperty<*, *>.referencedField: ObjProperty<*, *>
  get() {
    val ref = valueType.getRefType()
    val declaredReferenceFromChild =
      ref.target.refsFields.filter { it.valueType.getRefType().target == receiver && it != this } +
      setOf(ref.target.module,
            receiver.module).flatMap { it.extensions }.filter { it.valueType.getRefType().target == receiver && it.receiver == ref.target && it != this }
    if (declaredReferenceFromChild.isEmpty()) {
      error("Reference should be declared at both entities. It exist at ${receiver.name}#$name but absent at ${ref.target.name}")
    }
    if (declaredReferenceFromChild.size > 1) {
      error("""
        |More then one reference to ${receiver.name} declared at ${declaredReferenceFromChild[0].receiver.name}#${declaredReferenceFromChild[0].name}, 
        |${declaredReferenceFromChild[1].receiver.name}#${declaredReferenceFromChild[1].name}
        |""".trimMargin())
    }
    val referencedField = declaredReferenceFromChild[0]
    if (this.valueType.getRefType().child == referencedField.valueType.getRefType().child) {
      val (childStr, fix) = if (this.valueType.getRefType().child) {
        "child" to "Have you @Child annotation on both sides?"
      }
      else {
        "parent" to "Did you forget to add @Child annotation?"
      }
      error("Both fields ${receiver.name}#$name and ${ref.target.name}#${referencedField.name} are marked as $childStr. $fix")
    }
    return referencedField
  }