package com.intellij.workspace.api

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import com.intellij.util.containers.ConcurrentFactoryMap
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmName

/**
 * Stores information about entity class which is used to quickly find references to other entities inside a given entity.
 */
internal class EntityMetaData(val unmodifiableEntityType: Class<out TypedEntity>, val properties: Map<String, EntityPropertyKind>) {
  fun collectReferences(values: MutableMap<String, Any?>, collector: (Long) -> Unit) {
    for ((name, value) in values) {
      if (value == null) continue

      val kind = properties.getValue(name)

      @Suppress("UNCHECKED_CAST")
      when (kind) {
        is EntityPropertyKind.EntityReference -> collector((value as ProxyBasedEntityReferenceImpl<*>).id)
        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.EntityReference -> (value as List<ProxyBasedEntityReferenceImpl<*>>).forEach { collector(it.id) }
          is EntityPropertyKind.List -> error("List of lists are not supported")
          // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> Unit
          is EntityPropertyKind.DataClass -> (value as List<*>).forEach { itemKind.collectReferences(it, collector) }
          is EntityPropertyKind.EntityValue -> (value as List<Long>).forEach { collector(it) }
          EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> Unit
        }.let { } // exhaustive when
        // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> Unit
        is EntityPropertyKind.DataClass -> kind.collectReferences(value, collector)
        is EntityPropertyKind.EntityValue -> collector(value as Long)
        EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> Unit
      }.let { } // exhaustive when
    }
  }

  // TODO :: Try to unify this methods
  fun collectPersistentIdReferences(values: MutableMap<String, Any?>, collector: (PersistentEntityId<*>) -> Unit) {
    for ((name, value) in values) {
      if (value == null) continue

      val kind = properties.getValue(name)

      @Suppress("UNCHECKED_CAST")
      when (kind) {
        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.List -> error("List of lists are not supported")
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> (value as List<*>).forEach { itemKind.subclassesKinds.entries
              .find { subclassKinds -> subclassKinds.key.isInstance(it) }?.value?.collectPersistentIdReferences(it, collector)
          }
          is EntityPropertyKind.DataClass -> (value as List<*>).forEach { itemKind.collectPersistentIdReferences(it, collector) }
          is EntityPropertyKind.PersistentId -> (value as List<PersistentEntityId<*>>).forEach { collector(it) }
          else -> Unit
        }.let { } // exhaustive when
        is EntityPropertyKind.PersistentId -> collector((value as PersistentEntityId<*>))
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> kind.subclassesKinds.entries
          .find { subclassKinds -> subclassKinds.key.isInstance(value) }?.value?.collectPersistentIdReferences(value, collector)
        is EntityPropertyKind.DataClass -> kind.collectPersistentIdReferences(value, collector)
        else -> Unit
      }.let { } // exhaustive when
    }
  }

  fun replaceAllPersistentIdReferences(values: MutableMap<String, Any?>, oldEntity: PersistentEntityId<*>, newEntity: PersistentEntityId<*>) {
    // Property values already new copy of old properties
    val newValuesMap = mutableMapOf<String, Any?>()
    for ((name, value) in values) {
      if (value == null) continue

      val kind = properties.getValue(name)

      @Suppress("UNCHECKED_CAST")
      val newValue = when (kind) {
        is EntityPropertyKind.List -> when (val itemKind = kind.itemKind) {
          is EntityPropertyKind.List -> error("List of lists are not supported")
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> (value as List<*>).map { itemKind.subclassesKinds.entries
            .find { subclassKinds -> subclassKinds.key.isInstance(it) }?.value?.replaceAll(it, oldEntity, newEntity)
          }
          is EntityPropertyKind.DataClass -> (value as List<*>).map { itemKind.replaceAll(it, oldEntity, newEntity) }
          is EntityPropertyKind.PersistentId -> (value as List<PersistentEntityId<*>>).map { if (it == oldEntity) newEntity else it }
          else -> value
        }
        is EntityPropertyKind.PersistentId -> if (value == oldEntity) newEntity else value
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> kind.subclassesKinds.entries
          .find { subclassProperties -> subclassProperties.key.isInstance(value) }?.value?.replaceAll(value, oldEntity, newEntity)
        is EntityPropertyKind.DataClass -> kind.replaceAll(value, oldEntity, newEntity)
        else -> value
      }

      // If object changed we should replace it in original map
      if (value != newValue) values[name] = newValue
    }
  }

}

internal sealed class EntityPropertyKind {
  internal class Primitive(val clazz: Class<*>) : EntityPropertyKind()
  internal class SealedKotlinDataClassHierarchy(val subclassesKinds: Map<KClass<*>, DataClass>) : EntityPropertyKind()
  internal class DataClass(val dataClass: Class<*>, val properties: Map<String, EntityPropertyKind>,
                           private val referenceIdAccessors: kotlin.collections.List<kotlin.collections.List<Method>>,
                           private val referencePersistentIdAccessors: kotlin.collections.List<kotlin.collections.List<Method>>) : EntityPropertyKind() {

    val hasReferences = referenceIdAccessors.isNotEmpty()

    fun collectReferences(instance: Any?, collector: (Long) -> Unit) {
      fun collect(getters: kotlin.collections.List<Method>, getterIndex: Int, value: Any) {
        when {
          // TODO Write a test on entities removal referenced from list
          value is kotlin.collections.List<*> -> for (item in value) {
            if (item != null) {
              collect(getters, getterIndex, item)
            }
          }
          getterIndex >= getters.size -> {
            val id = value as Long?
            id?.let { collector(it) }
          }
          else -> {
            val nextValue = getters[getterIndex](value)
            if (nextValue != null) {
              collect(getters, getterIndex + 1, nextValue)
            }
          }
        }
      }

      if (instance == null) return

      for (accessors in referenceIdAccessors) {
        collect(accessors, 0, instance)
      }
    }

    fun collectPersistentIdReferences(instance: Any?, collector: (PersistentEntityId<*>) -> Unit) {
      fun collect(getters: kotlin.collections.List<Method>, getterIndex: Int, value: Any) {
        when {
          // TODO Write a test on entities removal referenced from list
          value is kotlin.collections.List<*> -> for (item in value) {
            if (item != null) {
              collect(getters, getterIndex, item)
            }
          }
          getterIndex >= getters.size -> {
            // A workaround of type erasure, otherwise we should use inline function with reified type parameter
            // In future, this and the method above can be generated
            val persistentId = value as PersistentEntityId<*>?
            persistentId?.let { collector(it) }
          }
          else -> {
            val nextValue = getters[getterIndex](value)
            if (nextValue != null) {
              collect(getters, getterIndex + 1, nextValue)
            }
          }
        }
      }

      if (instance == null) return

      for (accessors in referencePersistentIdAccessors) {
        collect(accessors, 0, instance)
      }
    }

    fun  replaceAll(instance: Any?,  oldElement: PersistentEntityId<*>, newElement: PersistentEntityId<*>) : Any?  {
      fun replaceProperty(getters: kotlin.collections.List<Method>, getterIndex: Int, value: Any): Any {
        when {
          value is kotlin.collections.List<*> -> return value.map { replaceProperty(getters, getterIndex, it!!) }
          getterIndex >= getters.size -> {
            val id = value as PersistentEntityId<*>
            return if (id == oldElement) newElement else value
          }
          else -> {
            val getterMethod = getters[getterIndex]
            val originPropertyValue = getterMethod(value)
            if (originPropertyValue == null) return value
            val propertyName = getterMethod.name.removePrefix("get").decapitalize()
            val propertyValue = replaceProperty(getters, getterIndex + 1, originPropertyValue)
            return if (propertyValue != originPropertyValue) value.copyWithPropertyReplace(propertyName, propertyValue) else value
          }
        }
      }

      if (instance == null) return null
      var originInstance: Any = instance
      for (accessors in referencePersistentIdAccessors) {
        val newInstance = replaceProperty(accessors, 0, originInstance)
        if (newInstance != originInstance) originInstance = newInstance
      }
      return originInstance
    }

    private fun Any.copyWithPropertyReplace(propertyName : String, propertyValue: Any): Any {
      val copyFunction = this::class.memberFunctions.first { it.name == "copy" }
      val instanceParam = copyFunction.instanceParameter!!
      val fieldParam = copyFunction.parameters.first { it.name == propertyName }
      return copyFunction.callBy(mapOf(instanceParam to this, fieldParam to propertyValue))!!
    }
  }

  internal class EntityValue(val clazz: Class<out TypedEntity>) : EntityPropertyKind()
  internal class List(val itemKind: EntityPropertyKind) : EntityPropertyKind()
  internal class EntityReference(val clazz: Class<out TypedEntity>) : EntityPropertyKind()
  internal class PersistentId(val clazz: Class<*>) : EntityPropertyKind()
  internal object FileUrl : EntityPropertyKind()
}

internal class EntityMetaDataRegistry {
  private val entityMetaData = ConcurrentFactoryMap.createMap { clazz: Class<out TypedEntity> -> calculateMetaData(clazz) }
  private val dataClassMetaData = ConcurrentFactoryMap.createMap { clazz: Class<*> -> calculateDataClassMeta(clazz) }

  fun getEntityMetaData(clazz: Class<out TypedEntity>): EntityMetaData = entityMetaData[clazz]!!
  fun getDataClassMetaData(clazz: Class<*>): EntityPropertyKind.DataClass = dataClassMetaData[clazz]!!

  private fun calculateDataClassMeta(clazz: Class<*>): EntityPropertyKind.DataClass {
    // TODO Assert it's a data class
    val propertiesMap = getPropertiesMap(clazz)
    val referenceIdAccessors = collectReferenceIdAccessors(clazz, propertiesMap)
    val referencePersistentIdAccessors = collectPersistentIdReferenceAccessors(clazz, propertiesMap)
    return EntityPropertyKind.DataClass(clazz, propertiesMap, referenceIdAccessors, referencePersistentIdAccessors)
  }

  private fun calculateMetaData(clazz: Class<out TypedEntity>): EntityMetaData {
    val properties = getPropertiesMap(clazz)
    return EntityMetaData(clazz, properties)
  }

  private fun collectReferenceIdAccessors(clazz: Class<*>, propertiesMap: Map<String, EntityPropertyKind>): List<List<Method>> {

    fun collect(kind: EntityPropertyKind, currentAccessor: List<Method>, result: MutableList<List<Method>>) {
      when (kind) {
        is EntityPropertyKind.EntityReference -> {
          result += currentAccessor + ProxyBasedEntityReferenceImpl::class.java.getMethod("getId")
        }
        is EntityPropertyKind.List -> {
          collect(kind.itemKind, currentAccessor, result)
        }
        is EntityPropertyKind.DataClass -> {
          for ((propertyName, propertyValue) in kind.properties) {
            collect(propertyValue, currentAccessor + listOf(kind.dataClass.getMethod("get${propertyName.capitalize()}")), result)
          }
        }
      }
    }

    val currentAccessor = emptyList<Method>()
    val result = mutableListOf<List<Method>>()
    propertiesMap.entries.forEach { (name, kind) ->
      collect(kind, currentAccessor + listOf(clazz.getMethod("get${name.capitalize()}")), result)
    }
    return result
  }

  private fun collectPersistentIdReferenceAccessors(clazz: Class<*>, propertiesMap: Map<String, EntityPropertyKind>): List<List<Method>> {

    fun collect(kind: EntityPropertyKind, currentAccessor: List<Method>, result: MutableList<List<Method>>) {
      when (kind) {
        is EntityPropertyKind.List -> {
          collect(kind.itemKind, currentAccessor, result)
        }
        is EntityPropertyKind.PersistentId -> {
          result += currentAccessor
        }
        is EntityPropertyKind.DataClass -> {
          for ((propertyName, propertyValue) in kind.properties) {
            collect(propertyValue, currentAccessor + listOf(kind.dataClass.getMethod("get${propertyName.capitalize()}")), result)
          }
        }
      }
    }

    val currentAccessor = emptyList<Method>()
    val result = mutableListOf<List<Method>>()
    propertiesMap.entries.forEach { (name, kind) ->
      collect(kind, currentAccessor + listOf(clazz.getMethod("get${name.capitalize()}")), result)
    }
    return result
  }

  private fun getPropertiesMap(clazz: Class<*>): Map<String, EntityPropertyKind> {
    //todo check restrictions
    // TODO Better check for data classes. Like final + all fields correspond to properties + all fields are final
    return clazz.methods.filter { it.name.startsWith("get") && it.name != "getClass" && it.name != "getEntitySource" && !it.isDefault }
      .associateBy({ it.name.removePrefix("get").decapitalize() }, { getPropertyKind(it.genericReturnType, clazz) })
  }

  @Suppress("UNCHECKED_CAST")
  private fun getPropertyKind(type: Type, owner: Class<*>): EntityPropertyKind = when (type) {
    is ParameterizedType -> when {
      type.rawType == List::class.java -> {
        val typeArgument = type.actualTypeArguments.single() as Class<*>
        val itemKind = getPropertyKind(typeArgument, owner)
        EntityPropertyKind.List(itemKind)
      }
      type.rawType == EntityReference::class.java -> {
        val typeArgument = type.actualTypeArguments.single() as Class<out TypedEntity>
        if (!TypedEntity::class.java.isAssignableFrom(typeArgument)) {
          error("ErrorReference type argument must be inherited from TypedEntity: $owner")
        }
        EntityPropertyKind.EntityReference(typeArgument)
      }
      // TODO?
      // TODO Test PersistentEntityId with type arguments
      type.rawType == PersistentEntityId::class.java -> EntityPropertyKind.PersistentId(PersistentEntityId::class.java)
      else -> error("Unsupported type in entities: $type in $owner")
    }
    is Class<*> -> when {
      type.isPrimitive || type == String::class.java || type.isEnum -> EntityPropertyKind.Primitive(type)
      TypedEntity::class.java.isAssignableFrom(type) -> EntityPropertyKind.EntityValue(type as Class<out TypedEntity>)
      type == VirtualFileUrl::class.java -> EntityPropertyKind.FileUrl
      PersistentEntityId::class.java.isAssignableFrom(type) -> {
        // TODO Make compatible with Java. Check Modifier.isFinal(type.modifiers), all fields are final etc
        if (!type.kotlin.isData) error("PersistentId class must be a Kotlin data class: $type")
        EntityPropertyKind.PersistentId(type)
      }
      // TODO Make compatible with Java. Check Modifier.isFinal(type.modifiers), all fields are final etc
      // TODO Fix objectInstance properties store in DataClass
      type.kotlin.isData || type.kotlin.objectInstance != null -> {
        val dataClassMetadata = dataClassMetaData[type]!!
        checkDataClassRestrictions(dataClassMetadata, allowReferences = true)
        dataClassMetadata
      }
      type.kotlin.isSealed -> {
        val subclasses = mutableListOf<KClass<*>>()
        fun collectSubclasses(root: KClass<*>) {
          subclasses.addAll(root.sealedSubclasses.filter { !it.isSealed && !it.isAbstract })
          root.sealedSubclasses.filter { it.isSealed }.forEach { collectSubclasses(it) }
        }
        collectSubclasses(type.kotlin)

        if (subclasses.isEmpty()) error("Empty subclasses list for sealed hierarchy inherited from ${type.kotlin}")
        val subclassesKinds = subclasses.filter { it.isData || it.objectInstance != null }
          .associateWith { getPropertyKind(it.java, owner) as EntityPropertyKind.DataClass }
        EntityPropertyKind.SealedKotlinDataClassHierarchy(subclassesKinds)
      }
      else -> throw IllegalArgumentException("Properties of type $type aren't allowed in entities")
    }
    else -> throw IllegalArgumentException("Properties of type $type aren't allowed in entities")
  }

  private fun checkDataClassRestrictions(metadata: EntityPropertyKind.DataClass, allowReferences: Boolean) {
    fun checkKind(kind: EntityPropertyKind) {
      when (kind) {
        is EntityPropertyKind.Primitive, is EntityPropertyKind.PersistentId, EntityPropertyKind.FileUrl -> Unit
        is EntityPropertyKind.EntityReference -> if (!allowReferences) {
          error("EntityReferences are unsupported in data classes: ${metadata.dataClass.name}")
        }
        else Unit
        is EntityPropertyKind.DataClass -> checkDataClassRestrictions(dataClassMetaData[kind.dataClass]!!, allowReferences)
        is EntityPropertyKind.List -> checkKind(kind.itemKind)
        is EntityPropertyKind.EntityValue -> error("Entities are unsupported in data classes: " + metadata.dataClass.name)
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
          kind.subclassesKinds.forEach { checkDataClassRestrictions(dataClassMetaData[it.key.java]!!, allowReferences) }
        }
      }.let { }  // exhaustive when
    }

    metadata.properties.values.forEach { kind -> checkKind(kind) }
  }
}

  private fun Hasher.putEntityPropertyKind(kind: EntityPropertyKind, metaDataRegistry: EntityMetaDataRegistry, visited: MutableSet<Class<*>>): Hasher {
    putUnencodedChars(kind.javaClass.name)
    return when (kind) {
      is EntityPropertyKind.EntityReference -> putEntityMetadata(metaDataRegistry.getEntityMetaData(kind.clazz), metaDataRegistry, visited)
      is EntityPropertyKind.List -> putEntityPropertyKind(kind.itemKind, metaDataRegistry, visited)
      is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
        kind.subclassesKinds.keys.sortedBy { it.jvmName }.forEach { putDataClassMetadata(metaDataRegistry.getDataClassMetaData(it.java), metaDataRegistry, visited) }
        this
      }
      is EntityPropertyKind.DataClass -> putDataClassMetadata(kind, metaDataRegistry, visited)
      is EntityPropertyKind.EntityValue -> putEntityMetadata(metaDataRegistry.getEntityMetaData(kind.clazz), metaDataRegistry, visited)
      EntityPropertyKind.FileUrl -> this
      is EntityPropertyKind.PersistentId -> putDataClassMetadata(metaDataRegistry.getDataClassMetaData(kind.clazz), metaDataRegistry, visited)
      is EntityPropertyKind.Primitive -> {
        putUnencodedChars(kind.clazz.name)

        if (kind.clazz.isEnum) {
          kind.clazz.enumConstants.forEach { putUnencodedChars((it as Enum<*>).name) }
        }

        this
      }
    }
  }

private fun Hasher.putEntityMetadata(metadata: EntityMetaData, metaDataRegistry: EntityMetaDataRegistry, visited: MutableSet<Class<*>>): Hasher {
  putUnencodedChars("ENTITY")
  putUnencodedChars(metadata.unmodifiableEntityType.name)

  if (!visited.add(metadata.unmodifiableEntityType)) {
    putUnencodedChars("VISITED")
    return this
  }

  for ((name, kind) in metadata.properties.entries.sortedBy { it.key }) {
    putUnencodedChars(name)
    putEntityPropertyKind(kind, metaDataRegistry, visited)
  }
  return this
}

private fun Hasher.putDataClassMetadata(metadata: EntityPropertyKind.DataClass, metaDataRegistry: EntityMetaDataRegistry, visited: MutableSet<Class<*>>): Hasher {
  putUnencodedChars("DATACLASS")
  putUnencodedChars(metadata.dataClass.name)

  if (!visited.add(metadata.dataClass)) {
    putUnencodedChars("VISITED")
    return this
  }

  putUnencodedChars("IS_OBJECT: ${metadata.dataClass.kotlin.objectInstance != null}")
  for ((name, kind) in metadata.properties.entries.sortedBy { it.key }) {
    putUnencodedChars(name)
    putEntityPropertyKind(kind, metaDataRegistry, visited)
  }
  return this
}

internal fun EntityPropertyKind.DataClass.hash(metaDataRegistry: EntityMetaDataRegistry, hasher: () -> Hasher = { Hashing.murmur3_128().newHasher() }): ByteArray {
  val h = hasher()
  h.putDataClassMetadata(this, metaDataRegistry, mutableSetOf())
  return h.hash().asBytes()
}

internal fun EntityMetaData.hash(metaDataRegistry: EntityMetaDataRegistry, hasher: () -> Hasher = { Hashing.murmur3_128().newHasher() }): ByteArray {
  val h = hasher()
  h.putEntityMetadata(this, metaDataRegistry, mutableSetOf())
  return h.hash().asBytes()
}
