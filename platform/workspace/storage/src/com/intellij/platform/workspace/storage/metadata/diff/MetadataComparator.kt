// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.diff

import com.intellij.platform.workspace.storage.metadata.diff.ComparisonUtil.compareMetadata
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata.EnumClassMetadata
import com.intellij.platform.workspace.storage.metadata.utils.collectTypesByFqn
import org.jetbrains.annotations.TestOnly

internal fun interface MetadataComparator<T> {
  fun areEquals(cache: T, current: T): ComparisonResult
}

/**
 * Used to compare [StorageTypeMetadata].
 *
 * [cacheTypesByFqn] and [currentTypesByFqn] are used to find "real" metadata class for [FinalClassMetadata.KnownClass] during comparison.
 * Because in case of cycled references between classes one reference is real metadata class (e.g. [FinalClassMetadata.ClassMetadata])
 * and another is [FinalClassMetadata.KnownClass] (stores just a class name).
 *
 * @property [comparedTypes] is used to resolve cycled references problem and also to speed up type comparison.
 * After types comparisons saves them in cache. So, it compares types pair from cache and current only once.
 *
 * @property propertiesComparator used to compare [StorageTypeMetadata.properties]
 */
internal class TypesMetadataComparator private constructor(
  private val cacheTypesByFqn: Map<String, StorageTypeMetadata>,
  private val currentTypesByFqn: Map<String, StorageTypeMetadata>
): MetadataComparator<StorageTypeMetadata> {
  private val propertiesComparator: MetadataComparator<PropertyMetadata> = PropertiesComparator(this)
  private val comparedTypes: MutableSet<Pair<String, String>> = mutableSetOf()

  internal constructor(cache: StorageTypeMetadata, current: StorageTypeMetadata):
    this(cache.collectTypesByFqn(), current.collectTypesByFqn())

  override fun areEquals(cache: StorageTypeMetadata, current: StorageTypeMetadata): ComparisonResult {
    if (comparedTypes.contains(cache.fqName to current.fqName)) {
      return Equal
    }

    val cacheType = findType(cache, cacheTypesByFqn)
    val currentType = findType(current, currentTypesByFqn)
    return typesAreEquals(cacheType, currentType)
  }

  private fun typesAreEquals(cache: StorageTypeMetadata, current: StorageTypeMetadata): ComparisonResult {
    comparedTypes.add(cache.fqName to current.fqName)
    return compareMetadata(cache, cache.fqName, current, current.fqName) {
      compare("name", cache.fqName, current.fqName, fqnsComparator)
      compareAll("supertypes", cache.supertypes, current.supertypes)

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
        compareAll("subclasses", cache.subclasses, current.subclasses, this@TypesMetadataComparator)
      } else {
        compareAll("properties",
                   notComputableProperties(cache.properties), notComputableProperties(current.properties), propertiesComparator)
      }
    }
  }

  private fun findType(type: StorageTypeMetadata, typesByFqn: Map<String, StorageTypeMetadata>) = typesByFqn[type.fqName] ?: type

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
      compare("isOpen", cache.isOpen, current.isOpen)
      compare("isComputable", cache.isComputable, current.isComputable)
      compare("withDefault", cache.withDefault, current.withDefault)
      compare("valueType", cache.valueType, current.valueType, valueTypesComparator)

      if (current is ExtPropertyMetadata) {
        cache as ExtPropertyMetadata
        compare("receiver name", cache.receiverFqn, current.receiverFqn, fqnsComparator)
      } else {
        cache as OwnPropertyMetadata
        current as OwnPropertyMetadata
        compare("isKey", cache.isKey, current.isKey)
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
          compare("isNullable", cache.isNullable, current.isNullable)
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
 * Used in [MetadataComparator] to compare classes fqns and to resolve type fqn from [StorageTypeMetadata]
 *
 * The method [replaceMetadataTypesFqnComparator] is needed to add separate logic during testing.
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.MetadataSerializationTest]
 */
internal var fqnsComparator: (String, String) -> Boolean = { cache, current -> cache == current }
  private set

@TestOnly
internal fun replaceMetadataTypesFqnComparator(comparator: (String, String) -> Boolean) {
  fqnsComparator = comparator
}
