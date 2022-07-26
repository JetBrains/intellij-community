// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.model

import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.ExtField
import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.deft.meta.impl.*
import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.WsData
import com.intellij.workspaceModel.codegen.deft.model.WsSealed
import com.intellij.workspaceModel.codegen.getRefType
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.deft.ValueType as OldValueType

/**
 * This is a temporary solution to build Obj meta structure from existing [DefType] classes. It should be replaced by code which works with
 * Kotlin PSI directly.
 */
fun convertToObjModules(typeDefs: List<DefType>, simpleTypes: List<DefType>, extFields: List<ExtField<*, *>>): List<CompiledObjModule> {
  val referencedTypes = findReferencedTypes(typeDefs)
  val packageToTypes = typeDefs.groupBy { it.packageName }
  val moduleStubs = packageToTypes.map { createObjModuleStub(it.key, it.value) }
  val externalModule = createObjModuleStub("com.intellij.workspaceModel.storage.bridgeEntities.api", referencedTypes.values.toList())
  val typeRegistry = DefTypeRegistry(moduleStubs.flatMap { it.types }.associateBy({ it.first.id }, { it.second }), 
                                     externalModule.types.associateBy({ it.first.id }, { it.second }), simpleTypes.associateBy { it.name })
  externalModule.registerContent(typeRegistry, extFields)
  return moduleStubs.map { it.registerContent(typeRegistry, extFields) }
}

private fun findReferencedTypes(defTypes: List<DefType>): Map<Int, DefType> {
  val result = defTypes.flatMap { defType ->
    defType.structure.declaredFields.mapNotNull {
      (it.type as? TRef)?.targetObjType as? DefType
    }
  }.associateByTo(HashMap()) { it.id }
  result.keys.removeAll(defTypes.map { it.id }.toSet())
  return result
}

private fun createObjTypeStub(defType: DefType, module: CompiledObjModuleImpl): ObjClassImpl<Obj> {
  val openness = when {
    defType.sealed -> ObjClass.Openness.enum
    defType.abstract -> ObjClass.Openness.abstract
    defType.open -> ObjClass.Openness.open
    else -> ObjClass.Openness.final
  }
  return ObjClassImpl(module, defType.name, openness)
}

private class DefTypeRegistry(private val typeByDefId: Map<Int, ObjClassImpl<Obj>>,
                              private val externalTypesByDefIt: Map<Int, ObjClassImpl<Obj>>,
                              private val simpleTypeByName: Map<String, DefType>) {
  fun findById(id: Int): ObjClassImpl<*>? = typeByDefId[id] ?: externalTypesByDefIt[id]
  fun findByName(name: String): DefType? = simpleTypeByName[name]
  fun findInheritors(name: String): List<DefType> = simpleTypeByName.values.filter { it.base?.name == name }
}

private class ObjModuleStub(val module: CompiledObjModuleImpl, val types: List<Pair<DefType, ObjClassImpl<Obj>>>) {
  fun registerContent(typeRegistry: DefTypeRegistry,
                      extFields: List<ExtField<*, *>>): CompiledObjModule {
    var extPropertyId = 0
    for ((defType, objType) in types) {
      for ((fieldId, field) in defType.structure.declaredFields.withIndex()) {
        objType.addField(convertOwnField<Any>(field, objType, typeRegistry, fieldId))
      }                         
      val addedSupers = HashSet<String>()
      
      listOfNotNull(defType.base).forEach { superType ->
        val objClass = (superType as? DefType)?.let { typeRegistry.findById(superType.id) }
        if (objClass != null) {
          objType.addSuperType(objClass)
          addedSupers.add(objClass.name)
        }
        else {
          objType.addSuperType(KtInterfaceType(superType.name))
          addedSupers.add(superType.name)
        }
      }
      defType.def.superTypes.filterNot { it.classifier in addedSupers }.forEach { 
        objType.addSuperType(KtInterfaceType(it.classifier))
      }
      extFields.filter { it.type.isRefType() && it.type.getRefType().targetObjType.id == defType.id }.forEach { extField ->
        module.addExtension(convertExtField(extField, typeRegistry.findById(extField.owner.id)!!, typeRegistry, module, extPropertyId++))  
      }
      module.addType(objType)
    }
    return module
  }
}

private fun createObjModuleStub(packageName: String, defTypes: List<DefType>): ObjModuleStub {
  val module = CompiledObjModuleImpl(packageName)
  val types = defTypes.sortedBy { it.name }.withIndex().map {
    it.value to createObjTypeStub(it.value, module)
  }
  return ObjModuleStub(module, types)
}

private fun <V> convertOwnField(field: Field<org.jetbrains.deft.Obj, Any?>,
                                receiver: ObjClassImpl<Obj>,
                                typeRegistry: DefTypeRegistry,
                                fieldId: Int): OwnProperty<Obj, V> {
  return OwnPropertyImpl(receiver, field.name, convertType(field.type, typeRegistry) as ValueType<V>, computeKind(field), field.open, field.content, field.constructorField, fieldId)
}

private fun computeKind(field: MemberOrExtField<*, *>): ObjProperty.ValueKind = when {
  field.defaultValue == null -> ObjProperty.ValueKind.Plain
  field.final -> ObjProperty.ValueKind.Computable(field.defaultValue!!)
  else -> ObjProperty.ValueKind.WithDefault(field.defaultValue!!)
}

private fun convertExtField(extField: ExtField<*, *>,
                            receiver: ObjClassImpl<*>,
                            typeRegistry: DefTypeRegistry,
                            module: CompiledObjModuleImpl,
                            extPropertyId: Int
): ExtProperty<*, *> {
  return ExtPropertyImpl(receiver, extField.name, convertType(extField.type, typeRegistry), computeKind(extField) , extField.open, extField.content, module, extPropertyId)
}

private fun <V> convertType(type: OldValueType<V>, typeRegistry: DefTypeRegistry): ValueType<*> = when (type) {
  is TBoolean -> ValueType.Boolean
  is TInt -> ValueType.Int
  is TBlob -> convertBlobType(type, typeRegistry)
  is TString -> ValueType.String
  is TList<*> -> ValueType.List(convertType(type.elementType, typeRegistry))
  is TSet<*> -> ValueType.Set(convertType(type.elementType, typeRegistry))
  is TMap<*, *> -> ValueType.Map(convertType(type.keyType, typeRegistry), convertType(type.valueType, typeRegistry))
  is TOptional<*> -> ValueType.Optional(convertType(type.type, typeRegistry))
  is TRef<*> -> ValueType.ObjRef(type.child, typeRegistry.findById(type.targetObjTypeId.id)!!)
  is TStructure<*, *> -> throw UnsupportedOperationException()
  else -> error(type)
}

private fun <V> convertBlobType(type: TBlob<V>, typeRegistry: DefTypeRegistry): ValueType<*> {
  val defType = typeRegistry.findByName(type.javaSimpleName)
  if (defType != null) {
    return convertToJvmType(defType, typeRegistry)
  }
  return ValueType.Blob<Any>(type.javaSimpleName, collectAllSupers(type.javaSimpleName, typeRegistry))
}

private fun convertToJvmType(defType: DefType,
                             typeRegistry: DefTypeRegistry): ValueType.JvmClass<*> = when (defType.def.kind) {
    WsSealed -> ValueType.SealedClass<Any>(defType.name, collectAllSupers(defType.name, typeRegistry),
                                           collectChildren(defType, typeRegistry))
    WsData -> ValueType.DataClass<Any>(defType.name, collectAllSupers(defType.name, typeRegistry),
                                  convertFieldsToDataClassProperties(defType, typeRegistry))
    else -> ValueType.Blob<Any>(defType.name, collectAllSupers(defType.name, typeRegistry))
  }

private fun collectChildren(defType: DefType, typeRegistry: DefTypeRegistry): List<ValueType.JvmClass<*>> {
  return typeRegistry.findInheritors(defType.name).map { convertToJvmType(it, typeRegistry) }
}

private fun convertFieldsToDataClassProperties(defType: DefType,
                                typeRegistry: DefTypeRegistry) =
  defType.structure.declaredFields.map { ValueType.DataClassProperty(it.name, convertType(it.type, typeRegistry)) }

private fun collectAllSupers(javaSimpleName: String, typeRegistry: DefTypeRegistry): List<String> {
  fun processSupers(javaSimpleName: String, result: MutableSet<String>) {
    val type = typeRegistry.findByName(javaSimpleName) ?: return
    type.def.superTypes.forEach {
      result.add(it.classifier)
      processSupers(it.classifier, result)
    }
  }

  val result = LinkedHashSet<String>()
  processSupers(javaSimpleName, result)
  return result.toList()
}
