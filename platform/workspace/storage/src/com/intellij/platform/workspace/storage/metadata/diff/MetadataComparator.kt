// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.diff

import com.intellij.platform.workspace.storage.metadata.diff.ComparisonUtil.compareAndPrintToLog
import com.intellij.platform.workspace.storage.metadata.diff.ComparisonUtil.compareMetadata
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata.EnumClassMetadata
import com.intellij.platform.workspace.storage.metadata.utils.MetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.metadata.utils.collectClassesByFqn

internal fun interface MetadataComparator<T> {
  fun areEquals(cache: T, current: T): ComparisonResult
}


/**
 * Used during deserialization to check that cache stores the supported version of entities and thus can be loaded.
 *
 * To check the versions it compares entities metadata from cache and current metadata.
 */
internal class CacheMetadataComparator: MetadataComparator<List<StorageTypeMetadata>> {
  override fun areEquals(cache: List<StorageTypeMetadata>, current: List<StorageTypeMetadata>): ComparisonResult {
    return compareAndPrintToLog("entities versions") {
      compareSubset("cache metadata", cache, current,
                  TypesComparator(collectAllClasses(cache), collectAllClasses(current)), classNameAsKey)
    }
  }

  private fun collectAllClasses(types: List<StorageTypeMetadata>): Map<String, StorageClassMetadata> {
    val classes: MutableMap<String, StorageClassMetadata> = hashMapOf()
    types.forEach { classes.putAll(it.collectClassesByFqn()) }
    return classes
  }
}

/**
 * Used to compare [StorageTypeMetadata].
 *
 * [cacheClasses] and [currentClasses] are used to find "real" metadata class for [FinalClassMetadata.KnownClass] during comparison.
 * Because in case of cycled references between classes one reference is real metadata class (e.g. [FinalClassMetadata.ClassMetadata])
 * and another is [FinalClassMetadata.KnownClass] (stores just a class name).
 *
 * @property [comparedTypes] is used to resolve cycled references problem and also to speed up type comparison.
 * After types comparisons saves them in cache. So, it compares types pair from cache and current only once.
 *
 * @property propertiesComparator used to compare [StorageTypeMetadata.properties]
 */
private class TypesComparator(private val cacheClasses: Map<String, StorageClassMetadata>,
                              private val currentClasses: Map<String, StorageClassMetadata>): MetadataComparator<StorageTypeMetadata> {
  private val propertiesComparator: MetadataComparator<PropertyMetadata> = PropertiesComparator(this)
  private val comparedTypes: MutableSet<Pair<String, String>> = mutableSetOf()

  override fun areEquals(cache: StorageTypeMetadata, current: StorageTypeMetadata): ComparisonResult {
    if (comparedTypes.contains(cache.fqName to current.fqName)) {
      return Equal
    }

    val cacheType = findType(cache, cacheClasses)
    val currentType = findType(current, currentClasses)
    return typesAreEquals(cacheType, currentType)
  }

  private fun typesAreEquals(cache: StorageTypeMetadata, current: StorageTypeMetadata): ComparisonResult {
    comparedTypes.add(cache.fqName to current.fqName)
    return compareMetadata(cache, cache.fqName, current, current.fqName) {
      compare("name", cache.fqName, current.fqName, fqnsComparator)
      skipComparison("supertypes")

      if (current is EntityMetadata) {
        cache as EntityMetadata
        compare("entity data", cache.entityDataFqName, current.entityDataFqName, fqnsComparator)
        compare("isAbstract", cache.isAbstract, current.isAbstract)
        compareAll("extension properties",
                   notComputableProperties(cache.extProperties), notComputableProperties(current.extProperties), propertiesComparator)
      }

      if (current is EnumClassMetadata) {
        cache as EnumClassMetadata
        compareAll("enum entries", cache.values, current.values.take(cache.values.size))
      }

      if (current is ExtendableClassMetadata) {
        cache as ExtendableClassMetadata
        compareSubset("subclasses", cache.subclasses, current.subclasses, this@TypesComparator, classNameAsKey)
      } else {
        compareAll("properties",
                   notComputableProperties(cache.properties), notComputableProperties(current.properties), propertiesComparator)
      }
    }
  }

  private fun findType(type: StorageTypeMetadata, classes: Map<String, StorageClassMetadata>) = classes[type.fqName] ?: type

  private fun notComputableProperties(properties: List<PropertyMetadata>) = properties.filterNot { it.isComputable }
}

/**
 * Used to compare [PropertyMetadata] of [StorageTypeMetadata].
 *
 * Features:
 * * Does not compare [PropertyMetadata.withDefault]. Because we can deserialize cache in all cases
 * * Does not compare [PropertyMetadata.isOpen]
 * * Does not compare [OwnPropertyMetadata.isKey]
 *
 * @property valueTypesComparator used to compare [PropertyMetadata.valueType].
 */
private class PropertiesComparator(typesComparator: MetadataComparator<StorageTypeMetadata>): MetadataComparator<PropertyMetadata> {
  private val valueTypesComparator: MetadataComparator<ValueTypeMetadata> = ValueTypesComparator(typesComparator)

  override fun areEquals(cache: PropertyMetadata, current: PropertyMetadata): ComparisonResult {
    return compareMetadata(cache, cache.name, current, current.name) {
      compare("name", cache.name, current.name)
      skipComparison("isOpen")
      compare("isComputable", cache.isComputable, current.isComputable)
      skipComparison("withDefault") // We can deserialize cache in all cases
      compare("valueType", cache.valueType, current.valueType, valueTypesComparator)

      if (current is ExtPropertyMetadata) {
        cache as ExtPropertyMetadata
        compare("receiver name", cache.receiverFqn, current.receiverFqn, fqnsComparator)
      } else {
        skipComparison("isKey")
      }
    }
  }
}

/**
 * Used to compare [ValueTypeMetadata] of [PropertyMetadata].
 *
 * Features:
 * * Cache can be loaded when we have not nullable type in cache and nullable type in the current version.
 *   So in this case [areEquals] returns true
 *
 * @property typesComparator used when value type is [ValueTypeMetadata.SimpleType.CustomType] to compare custom classes metadata.
 */
private class ValueTypesComparator(private val typesComparator: MetadataComparator<StorageTypeMetadata>): MetadataComparator<ValueTypeMetadata> {
  
  override fun areEquals(cache: ValueTypeMetadata, current: ValueTypeMetadata): ComparisonResult {
    return compareMetadata(cache, current) {
      when (current) {
        is ValueTypeMetadata.EntityReference -> {
          cache as ValueTypeMetadata.EntityReference
          compare("entityName", cache.entityFqName, current.entityFqName, fqnsComparator)
          compare("isChild", cache.isChild, current.isChild)
          compare("connectionType", cache.connectionType, current.connectionType)
        }
        is ValueTypeMetadata.ParameterizedType -> {
          cache as ValueTypeMetadata.ParameterizedType
          compare("primitive", cache.primitive, current.primitive, this@ValueTypesComparator)
          compareAll("generics", cache.generics, current.generics, this@ValueTypesComparator)
        }
        is ValueTypeMetadata.SimpleType -> {
          cache as ValueTypeMetadata.SimpleType
          // Cache can not be loaded only when we have nullable type in cache and not nullable type in the current version --> implication logic
          compare("isNullable", cache.isNullable, current.isNullable, ::implication)
          if (current is ValueTypeMetadata.SimpleType.PrimitiveType) {
            cache as ValueTypeMetadata.SimpleType.PrimitiveType
            compare("primitive type", cache.type, current.type)
          }
          if (current is ValueTypeMetadata.SimpleType.CustomType) {
            cache as ValueTypeMetadata.SimpleType.CustomType
            compare("custom type", cache.typeMetadata, current.typeMetadata, typesComparator)
          }
        }
      }
    }
  }
}


/**
 * Implication produces a value of false just in case the first operand is true and the second operand is false.
 *
 * Returns false if first = true and second = false, otherwise true.
 */
private fun implication(first: Boolean, second: Boolean) = !first || second


private val classNameAsKey: (StorageTypeMetadata) -> String =
  MetadataTypesFqnComparator.getInstance()::getTypeFqn

private val fqnsComparator: (String, String) -> Boolean =
  MetadataTypesFqnComparator.getInstance()::compareFqns
