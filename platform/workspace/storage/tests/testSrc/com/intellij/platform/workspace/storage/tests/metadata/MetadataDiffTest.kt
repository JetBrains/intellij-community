// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata

import com.intellij.platform.workspace.storage.impl.ConnectionId.ConnectionType
import com.intellij.platform.workspace.storage.metadata.diff.CacheMetadataComparator
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata.AbstractClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata.ClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata.KnownClass
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.EntityReference
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.SimpleType.CustomType
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.SimpleType.PrimitiveType
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetadataDiffTest {
  private val defaultDataClass = createFinalClass(
    fqName = "default data class",
    createOwnProperty("text", createPrimitiveType("String")),
    createOwnProperty("value", createPrimitiveType("Int")),
    createOwnProperty("bool", createPrimitiveType("Bool"))
  )

  @Test
  fun `changed property value type`() {
    val current = createMetadataEntity(
      createOwnProperty("version", createPrimitiveType("Int")),
      createOwnProperty("name", createPrimitiveType("String")),
      createOwnProperty("isSimple", createPrimitiveType("Boolean"))
    )

    val cache = createMetadataEntity(
      createOwnProperty("version", createPrimitiveType("String")),
      createOwnProperty("name", createPrimitiveType("String")),
      createOwnProperty("isSimple", createPrimitiveType("Boolean"))
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `changed property name`() {
    val current = createMetadataEntity(
      createOwnProperty("version", createPrimitiveType("Int")),
      createOwnProperty("name", createPrimitiveType("String")),
      createOwnProperty("isSimple", createPrimitiveType("Boolean"))
    )

    val cache = createMetadataEntity(
      createOwnProperty("version", createPrimitiveType("Int")),
      createOwnProperty("name", createPrimitiveType("String")),
      createOwnProperty("isHard", createPrimitiveType("Boolean"))
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }


  @Test
  fun `changed connection type`() {
    val current = createMetadataEntity(
      createOwnProperty("anotherEntity", createEntityReference(ConnectionType.ONE_TO_MANY))
    )

    val cache = createMetadataEntity(
      createOwnProperty("anotherEntity", createEntityReference(ConnectionType.ONE_TO_ONE))
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `nullable type to not nullable`() {
    val current = createMetadataEntity(
      createOwnProperty("someType", createPrimitiveType("Int", false))
    )

    val cache = createMetadataEntity(
      createOwnProperty("someType", createPrimitiveType("Int", true))
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `not nullable to nullable`() {
    val current = createMetadataEntity(
      createOwnProperty("someType", createPrimitiveType("Int", true))
    )

    val cache = createMetadataEntity(
      createOwnProperty("someType", createPrimitiveType("Int", false))
    )

    assertTrue(twoEntitiesDiff(current, cache))
  }


  @Test
  fun `changed data class property`() {
    val current = createMetadataEntity(
      createOwnProperty(
        "data",
        createCustomType(createFinalClass(
          fqName = "data class 1",
          createOwnProperty("text", createPrimitiveType("String")),
          createOwnProperty("key", createPrimitiveType("Int"))
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        "data",
        createCustomType(createFinalClass(
          fqName = "data class 1",
          createOwnProperty("text", createPrimitiveType("String")),
          createOwnProperty("key", createPrimitiveType("String"))
        ))
      )
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `changed data class in sealed class`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          createFinalClass(
            fqName = "data class 2",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("bool", createPrimitiveType("Boolean"))
          )
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          createFinalClass(
            fqName = "data class 2",
            createOwnProperty("value", createCustomType(defaultDataClass)),
            createOwnProperty("bool", createPrimitiveType("Boolean"))
          )
        ))
      )
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `new data class in sealed class`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          defaultDataClass,
          createFinalClass(
            fqName = "data class 2",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("bool", createPrimitiveType("Boolean"))
          )
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          createFinalClass(
            fqName = "data class 2",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("bool", createPrimitiveType("Boolean"))
          )
        ))
      )
    )

    assertTrue(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `removed data class in sealed class`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          defaultDataClass
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "data class 1",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("key", createPrimitiveType("Int"))
          ),
          createFinalClass(
            fqName = "data class 2",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("bool", createPrimitiveType("Boolean"))
          )
        ))
      )
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }


  @Test
  fun `cycled reference between data classes`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "first data class",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("secondDataClass", createCustomType(
              createFinalClass(
                fqName = "second data class",
                createOwnProperty("value", createPrimitiveType("Int")),
                createOwnProperty("firstDataClass", createCustomType(KnownClass(fqName = "first data class")))
              )
            ))
          ),
          KnownClass(fqName = "second data class")
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          KnownClass(fqName = "first data class"),
          createFinalClass(
            fqName = "second data class",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("firstDataClass", createCustomType(
              createFinalClass(
                fqName = "first data class",
                createOwnProperty("text", createPrimitiveType("String")),
                createOwnProperty("secondDataClass", createCustomType(KnownClass(fqName = "second data class")))
              )
            ))
          )
        ))
      )
    )

    assertTrue(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `cycled reference between data classes, changed class name`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "first data class",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("secondDataClass", createCustomType(
              createFinalClass(
                fqName = "second data class",
                createOwnProperty("value", createPrimitiveType("Int")),
                createOwnProperty("firstDataClass", createCustomType(KnownClass(fqName = "first data class")))
              )
            ))
          ),
          KnownClass(fqName = "second data class")
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "second data class",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("firstDataClass", createCustomType(
              createFinalClass(
                fqName = "first data class12",
                createOwnProperty("text", createPrimitiveType("String")),
                createOwnProperty("secondDataClass", createCustomType(KnownClass(fqName = "second data class")))
              )
            ))
          ),
          KnownClass(fqName = "first data class12")
        ))
      )
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }

  @Test
  fun `cycled reference between data classes, removed class`() {
    val current = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          createFinalClass(
            fqName = "first data class",
            createOwnProperty("text", createPrimitiveType("String")),
            createOwnProperty("secondDataClass", createCustomType(
              createFinalClass(
                fqName = "second data class",
                createOwnProperty("value", createPrimitiveType("Int")),
                createOwnProperty("firstDataClass", createCustomType(KnownClass(fqName = "first data class")))
              )
            ))
          )
        ))
      )
    )

    val cache = createMetadataEntity(
      createOwnProperty(
        name = "data",
        createCustomType(createAbstractClass(
          fqName = "sealed class 1",
          KnownClass(fqName = "first data class"),
          createFinalClass(
            fqName = "second data class",
            createOwnProperty("value", createPrimitiveType("Int")),
            createOwnProperty("firstDataClass", createCustomType(
              createFinalClass(
                fqName = "first data class",
                createOwnProperty("text", createPrimitiveType("String")),
                createOwnProperty("secondDataClass", createCustomType(KnownClass(fqName = "second data class")))
              )
            ))
          )
        ))
      )
    )

    assertFalse(twoEntitiesDiff(current, cache))
  }



  private fun twoEntitiesDiff(current: EntityMetadata, cache: EntityMetadata): Boolean {
    val comparisonResult = CacheMetadataComparator().areEquals(listOf(cache), listOf(current))
    println(comparisonResult.info)
    return comparisonResult.areEquals
  }


  private fun createMetadataEntity(vararg properties: OwnPropertyMetadata): EntityMetadata =
    EntityMetadata(fqName = "test", entityDataFqName = "test", extProperties = emptyList(), isAbstract = false,
                         properties = properties.toList(), supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"))


  private fun createAbstractClass(fqName: String, vararg subclasses: FinalClassMetadata): AbstractClassMetadata =
    AbstractClassMetadata(fqName = fqName, supertypes = emptyList(), subclasses = subclasses.toList())

  private fun createFinalClass(fqName: String, vararg properties: OwnPropertyMetadata): ClassMetadata =
    ClassMetadata(fqName = fqName, supertypes = emptyList(), properties = properties.toList())


  private fun createOwnProperty(name: String, valueType: ValueTypeMetadata): OwnPropertyMetadata =
    OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = name, valueType = valueType, withDefault = false)


  private fun createPrimitiveType(type: String, isNullable: Boolean = false): PrimitiveType = PrimitiveType(type = type, isNullable = isNullable)

  private fun createCustomType(customType: StorageClassMetadata, isNullable: Boolean = false): CustomType =
    CustomType(typeMetadata = customType, isNullable = isNullable)

  private fun createEntityReference(connectionType: ConnectionType, isNullable: Boolean = false): EntityReference =
    EntityReference(entityFqName = "target entity", isChild = false, connectionType = connectionType, isNullable = isNullable)
}