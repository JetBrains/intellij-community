package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.generateCode
import com.intellij.workspaceModel.codegen.impl.dsl.optInWorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.dsl.packageDirective
import com.intellij.workspaceModel.codegen.impl.metadata.model.getAbstractClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getCustomTypeConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getEntityMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getEnumClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getKnownClassConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getObjectMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.StorageTypeMetadata
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.javaDataName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFinalSubClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allSuperClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isAbstract
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isEntityRef
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getAllPropertiesWithOwnExtensions

/**
 * old method is [com.intellij.workspaceModel.codegen.impl.metadata.old.metadataStorageImplCode]
 */
fun GeneratorContext.metadataImpl(targetModule: CompiledObjModule, allModules: List<CompiledObjModule>): String {
  val metadataContext = MetadataContextImpl(this)
  return metadataContext.metadata(targetModule, allModules)
}

interface MetadataContext : GeneratorContext {
  fun buildPrimitiveValueType(valueType: ValueType<*>, isNullable: Boolean): String

  fun buildJvmClassValueType(valueTypeJvmClass: ValueType.JvmClass<*>, isNullable: Boolean): String
  
  fun reportError(message: String)
}

private class MetadataContextImpl(private val parentContext: GeneratorContext) : MetadataContext, GeneratorContext by parentContext {
  private val fqnsForInitializeMetadata: MutableSet<String> = linkedSetOf()
  private val fqnsForInitializeHash: MutableSet<String> = linkedSetOf()
  private val primitiveTypesMetadata = linkedSetOf<PrimitiveMetadata>()
  private val typeFqnToMetadata = mutableMapOf<String, String>()
  private var anyObjClassForErrorReporting: ObjClass<*>? = null

  override fun reportError(message: String) {
    val objClass = anyObjClassForErrorReporting ?: error("""
        Metadata generation encountered an error: $message. 
        Note: there are no entities to specify problem location.
        """.trimIndent())
    reportProblem(GenerationProblem(
      message,
      GenerationProblem.Level.ERROR,
      ProblemLocation.Class(objClass)
    ))
  }

  override fun buildPrimitiveValueType(valueType: ValueType<*>, isNullable: Boolean): String {
    val primitiveMetadata = PrimitiveMetadata(valueType.javaPrimitiveType, isNullable)
    primitiveTypesMetadata.add(primitiveMetadata)
    return primitiveMetadata.getVariableName()
  }

  override fun buildJvmClassValueType(valueTypeJvmClass: ValueType.JvmClass<*>, isNullable: Boolean): String {
    return getCustomTypeConstructor(isNullable, getClassMetadata(valueTypeJvmClass))
  }

  private fun CodeContext.initializeMetadata() {
    section("override fun initializeMetadata()") {
      for (primitiveType in primitiveTypesMetadata) {
        +"val ${primitiveType.getVariableName()} = ${primitiveType.getConstructor()}"
      }
      +"var typeMetadata: $StorageTypeMetadata"
      for (typeFqn in fqnsForInitializeMetadata) {
        val metaDataString = typeFqnToMetadata[typeFqn] ?: run {
          reportError("Metadata generation failed for $typeFqn")
          continue
        }
        +"typeMetadata = $metaDataString"
        +"${MetadataStorage.addMetadata}(typeMetadata)"
      }
    }
  }

  private fun CodeContext.initializeMetadataHash() {
    section("override fun initializeMetadataHash()") {
      for (typeFqn in fqnsForInitializeHash) {
        // TODO: we need special hash for tests!
        val metadataHash = typeFqnToMetadata[typeFqn]?.hashCode() ?: run {
          reportError("Metadata hash generation failed for $typeFqn")
          continue
        }
        +"${MetadataStorage.addMetadataHash}(typeFqn = $typeFqn, metadataHash = $metadataHash)"
      }
    }
  }

  // TODO: this just follows old logic, could be better
  fun metadata(targetModule: CompiledObjModule, allModules: List<CompiledObjModule>): String {
    val types = allModules.flatMap { it.types }
    types.firstOrNull()?.let { anyObjClassForErrorReporting = it }
    val abstractClasses = allModules.flatMap { it.abstractTypes }

    for (abstractClass in abstractClasses) {
      processAbstractClass(abstractClass)
    }

    for (entity in types) {
      processEntity(entity)
    }

    // TODO: here because old code had order abstract meta -> entities meta, hash -> abstract hash
    for (abstractClass in abstractClasses) {
      collectTypesForHash(abstractClass)
    }

    return generateCode {
      packageDirective(targetModule.implPackage)
      optInWorkspaceEntityInternalApi()
      section("internal object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.base}()") {
        initializeMetadata()
        initializeMetadataHash()
      }
    }
  }

  private fun processAbstractClass(abstractClass: ValueType.AbstractClass<*>) {
    for (finalSubclass in abstractClass.allFinalSubClasses) {
      val name = getName(finalSubclass)
      val metadata = getClassMetadata(finalSubclass)
      fqnsForInitializeMetadata.add(name)
    }
  }
  
  private fun processEntity(entity: ObjClass<*>) {
    val name = getFullName(entity)
    val metadata = entityMetadata(entity)
    fqnsForInitializeMetadata.add(name)
    fqnsForInitializeHash.add(name)
    collectTypesForHash(entity)
  }

  private fun collectTypesForHash(entity: ObjClass<*>) {
    getAllPropertiesWithOwnExtensions(entity).forEach {
      collectTypesForHash(it.valueType)
    }
  }

  private fun collectTypesForHash(valueType: ValueType<*>) {
    if (valueType is ValueType.JvmClass<*> && valueType !is ValueType.Blob<*>) {
      val name = getName(valueType)
      val metadata = getClassMetadata(valueType)
      fqnsForInitializeHash.add(name)
    }
    when (valueType) {
      is ValueType.AbstractClass -> valueType.subclasses.forEach { collectTypesForHash(it) }
      is ValueType.FinalClass<*> -> valueType.properties.forEach { collectTypesForHash(it.valueType) }
      is ValueType.Object<*> -> valueType.properties.forEach { collectTypesForHash(it.valueType) }
      is ValueType.Enum<*> -> valueType.properties.forEach { collectTypesForHash(it.valueType) }
      is ValueType.Optional<*> -> collectTypesForHash(valueType.type)
      is ValueType.Collection<*, *> -> collectTypesForHash(valueType.elementType)
      is ValueType.Map<*, *> -> {
        collectTypesForHash(valueType.keyType)
        collectTypesForHash(valueType.valueType)
      }
      else -> return
    }
  }

  private fun getClassMetadata(obj: ValueType.JvmClass<*>): String {
    return when (obj) {
      is ValueType.Blob<*> -> knownClassMetadata(obj)
      is ValueType.FinalClass<*> -> finalClassMetadata(obj)
      is ValueType.Object<*> -> objectMetadata(obj)
      is ValueType.AbstractClass<*> -> abstractClassMetadata(obj)
      is ValueType.Enum<*> -> enumMetadata(obj)
    }
  }

  private fun getOrComputeMetadata(jvmClass: ValueType.JvmClass<*>, compute: MetadataContext.(String) -> String): String {
    val name = getName(jvmClass)
    val existingMetadata = typeFqnToMetadata[name]
    if (existingMetadata != null) return existingMetadata
    val newMetadata = compute(name)
    typeFqnToMetadata[name] = newMetadata
    return newMetadata
  }

  private fun abstractClassMetadata(valueTypeAbstractClass: ValueType.AbstractClass<*>): String =
    getOrComputeMetadata(valueTypeAbstractClass) { name ->
      getAbstractClassMetadataConstructor(name,
                                          supertypes = getSuperClasses(valueTypeAbstractClass),
                                          subclasses = valueTypeAbstractClass.allFinalSubClasses.map { getClassMetadata(it) })
    }


  // Wrap this into getOrCompute to replace KnownClass with the actual metadata
  private fun knownClassMetadata(valueTypeJvmClass: ValueType.JvmClass<*>): String = getKnownClassConstructor(getName(valueTypeJvmClass))

  private fun finalClassMetadata(valueTypeFinalClass: ValueType.FinalClass<*>): String =
    getOrComputeMetadata(valueTypeFinalClass) { name ->
      getClassMetadataConstructor(name,
                                  supertypes = getSuperClasses(valueTypeFinalClass),
                                  properties = valueTypeFinalClass.properties.map { buildPropertyMetadata(it) })
    }

  private fun objectMetadata(valueTypeObject: ValueType.Object<*>): String =
    getOrComputeMetadata(valueTypeObject) { name ->
      getObjectMetadataConstructor(name,
                                   supertypes = getSuperClasses(valueTypeObject),
                                   properties = valueTypeObject.properties.map { buildPropertyMetadata(it) })
    }

  private fun enumMetadata(valueTypeEnum: ValueType.Enum<*>): String =
    getOrComputeMetadata(valueTypeEnum) { name ->
      getEnumClassMetadataConstructor(name,
                                      supertypes = getSuperClasses(valueTypeEnum),
                                      values = allWithDoubleQuotesAndEscapedDollar(valueTypeEnum.values),
                                      properties = valueTypeEnum.properties.map { buildPropertyMetadata(it) })
    }

  private fun entityMetadata(objClass: ObjClass<*>): String {
    val name = getFullName(objClass)
    val existingMetadata = typeFqnToMetadata[name]
    if (existingMetadata != null) return existingMetadata
    val newMetadata = getEntityMetadataConstructor(
      fqName = name,
      entityDataFqName = getJavaFullName(objClass.javaDataName, objClass.module.implPackage),
      supertypes = objClass.allSuperClasses.map { getFullName(it) },
      properties = getAllPropertiesWithOwnExtensions(objClass).map { buildPropertyMetadata(it) },
      extProperties = objClass.module.extensions
        .filter {
          val unwrapped = unwrapReferenceType(it.valueType)
          // isEntityRef == true && unwrapped == null should be impossible
          it.valueType.isEntityRef(it) && unwrapped != null && unwrapped.target == objClass
        }
        .map { buildPropertyMetadata(it) },
      isAbstract = objClass.isAbstract
    )
    typeFqnToMetadata[name] = newMetadata
    return newMetadata
  }
}
