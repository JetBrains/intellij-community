package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.metadata.withDoubleQuotes
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isOverride
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.safeAnnotations
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapValueType

internal fun ValueType<*>.isVfuType(): Boolean {
  val unwrapped = unwrapValueType(this)
  return unwrapped is ValueType.Blob && unwrapped.kotlinClassName == VirtualFileUrl.decoded
}

internal sealed interface VfuProperty {
  val name: String
  val access: String
  
  val quotedName: String
    get() = name.withDoubleQuotes()
  
  val indexAccess: String
    get() = "this.$access"
  
  val setterAccess: String
    get() = "value${access.dropWhile { it != '.' }}"
}

private data class SimpleVfuProperty(override val name: String) : VfuProperty {
  override val access: String
    get() = name
}

private data class CollectionVfuProperty(override val name: String, override val access: String) : VfuProperty

private fun OwnProperty<*, *>.isIndexVfuAnnotated() = safeAnnotations.firstOrNull { it.fqName == IndexVfu.decoded } != null

internal typealias VfuProperties = Map<String, List<VfuProperty>>

internal fun getVfuProperties(objClass: ObjClass<*>): Map<String, List<VfuProperty>> {
  val nameToVfus = getAllProperties(objClass, withComputable = false, withSymbolicId = false, withRefs = false, withEntitySource = false)
    .filter { !it.isOverride && it.isIndexVfuAnnotated() }
    .associate {
      val result = arrayListOf<VfuProperty>()
      collectVfus(ValueType.ClassProperty(it.name, it.valueType), result, "")
      it.name to result
    }
  return nameToVfus
}

private fun collectVfus(property: ValueType.ClassProperty<*>, result: MutableList<VfuProperty>, prefix: String) {
  val (propertyName, valueType) = property
  when (valueType) {
    is ValueType.Blob -> {
      if (valueType.kotlinClassName == VirtualFileUrl.decoded)
        result.add(SimpleVfuProperty("$prefix$propertyName"))
    }
    is ValueType.Optional<*> -> {
      if (valueType.type.isVfuType())
        result.add(SimpleVfuProperty("$prefix$propertyName"))
    }
    is ValueType.Collection<*, *> -> {
      val elementType = valueType.elementType
      if (elementType.isVfuType()) {
        result.add(CollectionVfuProperty("$prefix$propertyName", "$prefix$propertyName"))
        return
      }
      if (elementType is ValueType.FinalClass<*>) {
        val midList = arrayListOf<VfuProperty>()
        for (classProperty in elementType.properties) {
          collectVfus(classProperty, midList, "")
        }
        for (midProperty in midList) {
          if (midProperty is SimpleVfuProperty)
            result.add(CollectionVfuProperty("$prefix$propertyName.${midProperty.name}",
                                             "$prefix$propertyName.map { it.${midProperty.name} }"))
          else {
            result.add(CollectionVfuProperty("$prefix$propertyName.${midProperty.name}",
                                             "$prefix$propertyName.flatMap { it.${midProperty.access} }"))
          }
        }
      }
    }
    is ValueType.FinalClass<*> -> {
      for (property in valueType.properties) {
        collectVfus(property, result, "$prefix$propertyName.")
      }
    }
    is ValueType.Object<*> -> TODO()
    // is ValueType.AbstractClass<*> -> TODO()
    // is ValueType.Map<*, *> -> TODO()
    // is ValueType.Structure<*> -> TODO()
    else -> return
  }
}