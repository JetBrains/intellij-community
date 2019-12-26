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
          is EntityPropertyKind.Class -> (value as List<*>).forEach { itemKind.collectReferences(it, collector) }
          is EntityPropertyKind.EntityValue -> (value as List<Long>).forEach { collector(it) }
          EntityPropertyKind.FileUrl, is EntityPropertyKind.PersistentId, is EntityPropertyKind.Primitive -> Unit
        }.let { } // exhaustive when
        // TODO EntityReferences/EntityValues are not supported in sealed hierarchies
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> Unit
        is EntityPropertyKind.Class -> kind.collectReferences(value, collector)
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
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
            (value as List<*>).forEach {
              itemKind.subclassesProperties.forEach { subclassProperties ->
                if (subclassProperties.key.isInstance(it)) subclassProperties.value.collectReferences(it, collector)
              }
            }
          }
          is EntityPropertyKind.Class -> (value as List<*>).forEach { itemKind.collectReferences(it, collector) }
          is EntityPropertyKind.PersistentId -> (value as List<PersistentEntityId<*>>).forEach { collector(it) }
          else -> Unit
        }.let { } // exhaustive when
        is EntityPropertyKind.PersistentId -> collector((value as PersistentEntityId<*>))
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
          kind.subclassesProperties.forEach { subclassProperties ->
            if (subclassProperties.key.isInstance(value)) subclassProperties.value.collectReferences(value, collector)
          }
        }
        is EntityPropertyKind.Class -> kind.collectReferences(value, collector)
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
          is EntityPropertyKind.SealedKotlinDataClassHierarchy -> (value as List<*>).map {
            return@map itemKind.subclassesProperties.entries.filter { subclassProperties -> subclassProperties.key.isInstance(it) }
              .map { subclassProperties -> subclassProperties.value.replaceAll(it, oldEntity, newEntity) }
              .first()
          }
          is EntityPropertyKind.Class -> (value as List<*>).map { itemKind.replaceAll(it, oldEntity, newEntity) }
          is EntityPropertyKind.PersistentId -> (value as List<PersistentEntityId<*>>).map { if (it == oldEntity) newEntity else it }
          else -> value
        }
        is EntityPropertyKind.PersistentId -> if (value == oldEntity) newEntity else value
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> kind.subclassesProperties.entries
          .filter { subclassProperties -> subclassProperties.key.isInstance(value) }
          .map { subclassProperties -> subclassProperties.value.replaceAll(value, oldEntity, newEntity) }
          .first()
        is EntityPropertyKind.Class -> kind.replaceAll(value, oldEntity, newEntity)
        else -> value
      }

      // If object changed we should replace it in original map
      if (value != newValue) newValuesMap[name] = newValue
    }

    newValuesMap.forEach { (propertyName, propertyValue) -> values.replace(propertyName, propertyValue) }
  }

}

internal sealed class EntityPropertyKind {
  internal class Primitive(val clazz: java.lang.Class<*>) : EntityPropertyKind()
  internal class SealedKotlinDataClassHierarchy(val subclassesProperties: Map<KClass<*>, Class>) : EntityPropertyKind()
  internal class Class(val aClass: java.lang.Class<*>, val properties: Map<String, EntityPropertyKind>,
                       private val referenceAccessors: kotlin.collections.List<kotlin.collections.List<Method>>) : EntityPropertyKind() {

    val hasReferences = referenceAccessors.isNotEmpty()

    fun <T> collectReferences(instance: Any?, collector: (T) -> Unit) {
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
            // For this method it's ok. It uses for getting soft and hard links here (PersistentId or Long)
            try {
              val id = value as? T
              id?.let { collector(it) }
            } catch(e: ClassCastException) { }
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

      for (accessors in referenceAccessors) {
        collect(accessors, 0, instance)
      }
    }

    fun <T> replaceAll(instance: Any?,  oldElement: T, newElement: T) : Any?  {
      val originToCloned = mutableMapOf<Any, MutableList<Any>>()
      val callStackMap = LinkedHashMap<Any, Pair<Method, Any>>()
      fun collect(getters: kotlin.collections.List<Method>, getterIndex: Int, value: Any) {
        when {
          // TODO Write a test on entities removal referenced from list
          value is kotlin.collections.List<*> -> for (item in value) {
            if (item != null) {
              callStackMap[item] = getters[getterIndex - 1] to value
              collect(getters, getterIndex, item)
            }
          }
          getterIndex >= getters.size -> {
            val id = value as T?
            if (id != null && id == oldElement) {
              val fieldName = getters[getterIndex - 1].name.removePrefix("get").decapitalize()
              val objectInstance = callStackMap[value]!!.second
              originToCloned.computeIfAbsent(objectInstance) { mutableListOf() }
                .add(objectInstance.copyWithPropertyReplace(fieldName, newElement as Any))
            }
          }
          else -> {
            val nextValue = getters[getterIndex](value)
            if (nextValue != null) {
              callStackMap[nextValue] = getters[getterIndex] to value
              collect(getters, getterIndex + 1, nextValue)
            }
          }
        }
      }

      if (instance == null) return null
      for (accessors in referenceAccessors) {
        collect(accessors, 0, instance)
      }

      val handledLists = mutableSetOf<Any>()
      if (originToCloned.isNotEmpty()) {
        callStackMap.entries.reversed().forEach { entity ->
          val originObj = entity.key
          val clonedObj = originToCloned[originObj]
          if (clonedObj == null || clonedObj.isEmpty()) return@forEach

          val objectInstance = entity.value.second

          val (propertyValue, ownerMetadata) = if (objectInstance is kotlin.collections.List<*> && handledLists !in objectInstance) {
            val newList = mutableListOf<Any>()
            objectInstance.forEach {
              val clonedObjectsList = originToCloned[it]
              if (clonedObjectsList.isNullOrEmpty()) newList += it!! else newList += clonedObjectsList.first()
            }
            handledLists += objectInstance
            newList to callStackMap[objectInstance]
          }
          else clonedObj to entity.value

          val method = ownerMetadata!!.first
          val originInstance = ownerMetadata.second

          val propertyName = method.name.removePrefix("get").decapitalize()
          originToCloned.computeIfAbsent(originInstance) { mutableListOf() }
            .add(originInstance.copyWithPropertyReplace(propertyName, propertyValue))
        }
      }

      // For root instance there should be only one record in list (List only for the case there temporary root is collection)
      return originToCloned[instance]?.first() ?: instance
    }

    private fun Any.copyWithPropertyReplace(propertyName : String, propertyValue: Any): Any {
      val copyFunction = this::class.memberFunctions.first { it.name == "copy" }
      val instanceParam = copyFunction.instanceParameter!!
      val fieldParam = copyFunction.parameters.first { it.name == propertyName }
      return copyFunction.callBy(mapOf(instanceParam to this, fieldParam to propertyValue))!!
    }
  }

  internal class EntityValue(val clazz: java.lang.Class<out TypedEntity>) : EntityPropertyKind()
  internal class List(val itemKind: EntityPropertyKind) : EntityPropertyKind()
  internal class EntityReference(val clazz: java.lang.Class<out TypedEntity>) : EntityPropertyKind()
  internal class PersistentId(val clazz: java.lang.Class<*>) : EntityPropertyKind()
  internal object FileUrl : EntityPropertyKind()
}

internal class EntityMetaDataRegistry {
  private val entityMetaData = ConcurrentFactoryMap.createMap { clazz: Class<out TypedEntity> -> calculateMetaData(clazz) }
  private val classMetaData = ConcurrentFactoryMap.createMap { clazz: Class<*> -> calculateDataClassMeta(clazz) }

  fun getEntityMetaData(clazz: Class<out TypedEntity>): EntityMetaData = entityMetaData[clazz]!!
  fun getClassMetaData(clazz: Class<*>): EntityPropertyKind.Class = classMetaData[clazz]!!

  private fun calculateDataClassMeta(clazz: Class<*>): EntityPropertyKind.Class {
    // TODO Assert it's a data class
    val propertiesMap = getPropertiesMap(clazz)
    val referenceAccessors = ArrayList<List<Method>>()
    collectReferenceAccessors(clazz, propertiesMap, emptyList(), referenceAccessors)
    return EntityPropertyKind.Class(clazz, propertiesMap, referenceAccessors)
  }

  private fun calculateMetaData(clazz: Class<out TypedEntity>): EntityMetaData {
    val properties = getPropertiesMap(clazz)
    return EntityMetaData(clazz, properties)
  }

  private fun collectReferenceAccessors(clazz: Class<*>, propertiesMap: Map<String, EntityPropertyKind>, currentAccessor: List<Method>,
                                        result: MutableList<List<Method>>) {

    fun collect(kind: EntityPropertyKind, currentAccessor: List<Method>, result: MutableList<List<Method>>) {
      when (kind) {
        is EntityPropertyKind.EntityReference -> {
          result += currentAccessor + ProxyBasedEntityReferenceImpl::class.java.getMethod("getId")
        }
        is EntityPropertyKind.List -> {
          collect(kind.itemKind, currentAccessor, result)
        }
        is EntityPropertyKind.PersistentId -> {
          result += currentAccessor
        }
        is EntityPropertyKind.Class -> {
          for ((propertyName, propertyValue) in kind.properties) {
            collect(propertyValue, currentAccessor + listOf(kind.aClass.getMethod("get${propertyName.capitalize()}")), result)
          }
        }
      }
    }

    propertiesMap.entries.forEach { (name, kind) ->
      collect(kind, currentAccessor + listOf(clazz.getMethod("get${name.capitalize()}")), result)
    }
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
      type.kotlin.isData || type.kotlin.objectInstance != null -> {
        val classMetadata = classMetaData[type]!!
        checkClassRestrictions(classMetadata, allowReferences = true)
        classMetadata
      }
      type.kotlin.isSealed -> {
        val subclasses = mutableListOf<KClass<*>>()
        fun collectSubclasses(root: KClass<*>) {
          subclasses.addAll(root.sealedSubclasses.filter { !it.isSealed && !it.isAbstract })
          root.sealedSubclasses.filter { it.isSealed }.forEach { collectSubclasses(it) }
        }
        collectSubclasses(type.kotlin)

        if (subclasses.isEmpty()) error("Empty subclasses list for sealed hierarchy inherited from ${type.kotlin}")
        val result = mutableMapOf<KClass<*>, EntityPropertyKind.Class>()
        for (subclass in subclasses) {
          if (!subclass.isData && subclass.objectInstance == null) error("Subclass $subclass must a data class or an object")
          val propertyKind = getPropertyKind(subclass.java, owner)
          result[subclass] = propertyKind as EntityPropertyKind.Class
        }

        EntityPropertyKind.SealedKotlinDataClassHierarchy(result)
      }
      else -> throw IllegalArgumentException("Properties of type $type aren't allowed in entities")
    }
    else -> throw IllegalArgumentException("Properties of type $type aren't allowed in entities")
  }

  private fun checkClassRestrictions(metadata: EntityPropertyKind.Class, allowReferences: Boolean) {
    fun checkKind(kind: EntityPropertyKind) {
      when (kind) {
        is EntityPropertyKind.Primitive, is EntityPropertyKind.PersistentId, EntityPropertyKind.FileUrl -> Unit
        is EntityPropertyKind.EntityReference -> if (!allowReferences) {
          error("EntityReferences are unsupported in data classes: ${metadata.aClass.name}")
        }
        else Unit
        is EntityPropertyKind.Class -> checkClassRestrictions(classMetaData[kind.aClass]!!, allowReferences)
        is EntityPropertyKind.List -> checkKind(kind.itemKind)
        is EntityPropertyKind.EntityValue -> error("Entities are unsupported in data classes: " + metadata.aClass.name)
        is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
          kind.subclassesProperties.forEach { checkClassRestrictions(classMetaData[it.key.java]!!, allowReferences) }
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
        kind.subclassesProperties.keys.sortedBy { it.jvmName }.forEach { putDataClassMetadata(metaDataRegistry.getClassMetaData(it.java), metaDataRegistry, visited) }
        this
      }
      is EntityPropertyKind.Class -> putDataClassMetadata(kind, metaDataRegistry, visited)
      is EntityPropertyKind.EntityValue -> putEntityMetadata(metaDataRegistry.getEntityMetaData(kind.clazz), metaDataRegistry, visited)
      EntityPropertyKind.FileUrl -> this
      is EntityPropertyKind.PersistentId -> putDataClassMetadata(metaDataRegistry.getClassMetaData(kind.clazz), metaDataRegistry, visited)
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

private fun Hasher.putDataClassMetadata(metadata: EntityPropertyKind.Class, metaDataRegistry: EntityMetaDataRegistry, visited: MutableSet<Class<*>>): Hasher {
  putUnencodedChars("DATACLASS")
  putUnencodedChars(metadata.aClass.name)

  if (!visited.add(metadata.aClass)) {
    putUnencodedChars("VISITED")
    return this
  }

  putUnencodedChars("IS_OBJECT: ${metadata.aClass.kotlin.objectInstance != null}")
  for ((name, kind) in metadata.properties.entries.sortedBy { it.key }) {
    putUnencodedChars(name)
    putEntityPropertyKind(kind, metaDataRegistry, visited)
  }
  return this
}

internal fun EntityPropertyKind.Class.hash(metaDataRegistry: EntityMetaDataRegistry, hasher: () -> Hasher = { Hashing.murmur3_128().newHasher() }): ByteArray {
  val h = hasher()
  h.putDataClassMetadata(this, metaDataRegistry, mutableSetOf())
  return h.hash().asBytes()
}

internal fun EntityMetaData.hash(metaDataRegistry: EntityMetaDataRegistry, hasher: () -> Hasher = { Hashing.murmur3_128().newHasher() }): ByteArray {
  val h = hasher()
  h.putEntityMetadata(this, metaDataRegistry, mutableSetOf())
  return h.hash().asBytes()
}
