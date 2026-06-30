package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isComputable
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isEntityRef
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.ownExtensions
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapValueType

internal fun getAllReferenceProperties(objClass: ObjClass<*>, onlyParents: Boolean = false): List<OwnProperty<*, *>> {
  val allReferences = PropertiesCache.getReferences(objClass)
  if (!onlyParents) {
    return allReferences.map { it.first }
  }
  return allReferences.filterNot { it.second.child }.map { it.first }
}

internal fun getAllPropertiesWithOwnExtensions(objClass: ObjClass<*>): List<ObjProperty<*, *>> =
  getAllProperties(objClass, true) + objClass.ownExtensions.filterNot { it.valueType.isEntityRef(it) }

internal fun getAllProperties(
  objClass: ObjClass<*>,
  withComputable: Boolean = false,
  withSymbolicId: Boolean = true,
  withRefs: Boolean = true,
  withEntitySource: Boolean = true,
  withOptional: Boolean = true,
  withDefault: Boolean = true,
): List<OwnProperty<*, *>> {
  return PropertiesCache[objClass].filter { objProperty ->
    val isSymbolicId = objProperty.name == symbolicIdFieldName
    if (!withSymbolicId && objProperty.name == symbolicIdFieldName) return@filter false
    if (!withComputable && !isSymbolicId && objProperty.valueKind is ObjProperty.ValueKind.Computable) return@filter false
    if (!withRefs && objProperty.valueType.isReferenceType()) return@filter false
    if (!withEntitySource && objProperty.name == entitySourceFieldName) return@filter false
    if (!withOptional && objProperty.valueType is ValueType.Optional<*>) return@filter false
    if (!withDefault && objProperty.valueKind !is ObjProperty.ValueKind.Plain) return@filter false
    return@filter true
  }
}

// TODO: move computed properties into some ProcessedObjClass
private object PropertiesCache {
  private val cache: MutableMap<ObjClass<*>, List<OwnProperty<*, *>>> = mutableMapOf()
  private val referencesCache: MutableMap<ObjClass<*>, List<Pair<OwnProperty<*, *>, ValueType.ObjRef<*>>>> = mutableMapOf()

  operator fun get(objClass: ObjClass<*>): List<OwnProperty<*, *>> {
    return cache[objClass] ?: collectProperties(objClass)
  }
  
  fun getReferences(objClass: ObjClass<*>): List<Pair<OwnProperty<*, *>, ValueType.ObjRef<*>>> {
    return referencesCache[objClass] ?: collectReferenceProperties(objClass)
  }

  private fun collectProperties(objClass: ObjClass<*>): List<OwnProperty<*, *>> {
    val propertiesByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectProperties(objClass, propertiesByName)
    val result = propertiesByName.values.toList()
    cache[objClass] = result
    return result
  }

  private fun collectProperties(objClass: ObjClass<*>, propertiesByName: MutableMap<String, OwnProperty<*, *>>) {
    for (superType in objClass.superTypes) {
      if (superType is ObjClass<*>) {
        collectProperties(superType, propertiesByName)
      }
    }
    for (property in objClass.fields) {
      propertiesByName.remove(property.name)
      propertiesByName[property.name] = property
    }
  }
  
  private fun collectReferenceProperties(objClass: ObjClass<*>): List<Pair<OwnProperty<*, *>, ValueType.ObjRef<*>>> {
    val result: List<Pair<OwnProperty<*, *>, ValueType.ObjRef<*>>> = PropertiesCache[objClass].filter { !it.isComputable }.mapNotNull {
      val unwrapped = unwrapValueType(it.valueType)
      if (unwrapped is ValueType.ObjRef<*>) it to unwrapped
      else null
    }
    referencesCache[objClass] = result
    return result
  }
}

// TODO: entity source is always mandatory and should always be present
fun mandatoryProperties(objClass: ObjClass<*>): List<ObjProperty<*, *>> {
  val properties = getAllProperties(objClass, withSymbolicId = false, withRefs = false, withOptional = false, withDefault = false)
  if (properties.isNotEmpty()) {
    return properties.filterNot { it.name == entitySourceFieldName } + properties.single { it.name == entitySourceFieldName }
  }
  return properties
}

fun collectionProperties(objClass: ObjClass<*>): List<OwnProperty<*, *>> {
  return PropertiesCache[objClass].filter { objProperty ->
    if (objProperty.valueKind is ObjProperty.ValueKind.Computable) return@filter false
    if (objProperty.valueType.isReferenceType()) return@filter false
    return@filter objProperty.valueType is ValueType.Collection<*, *>
  }
}
