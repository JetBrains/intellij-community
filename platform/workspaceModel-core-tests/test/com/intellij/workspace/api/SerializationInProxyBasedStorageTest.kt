package com.intellij.workspace.api

import com.intellij.testFramework.rules.TempDirectory
import org.junit.Rule
import org.junit.Test

internal data class SampleDataClassForSerialization(
  val url: VirtualFileUrl
)

internal interface SampleEntityForSerialization : TypedEntity, ReferableTypedEntity {
  val parent: SampleEntity
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val children: List<SampleEntity>
  val fileProperty: VirtualFileUrl
  val dataClasses: List<SampleDataClassForSerialization>
}

internal interface ModifiableSampleEntityForSerialization : SampleEntityForSerialization, ModifiableTypedEntity<SampleEntityForSerialization> {
  override var booleanProperty: Boolean
  override var stringProperty: String
  override var stringListProperty: MutableList<String>
  override var stringMapProperty: MutableMap<String, String>
  override var fileProperty: VirtualFileUrl
  override var parent: SampleEntity
  override var dataClasses: List<SampleDataClassForSerialization>
  override var children: List<SampleEntity>
}

internal data class SampleEntityForSerializationSource(val name: String) : EntitySource

class SerializationInProxyBasedStorageTest {
  @Rule @JvmField val tempDir = TempDirectory()

  @Test
  fun empty() {
    verifySerializationRoundTrip(TypedEntityStorageBuilder.createProxy().toStorage())
  }

  @Test
  fun smoke() {
    val tempFolder = tempDir.newFolder()

    val builder = TypedEntityStorageBuilder.createProxy()
    val sampleEntity = builder.addSampleEntity("ggg", SampleEntitySource("y"), true, mutableListOf("5", "6"), tempFolder.toVirtualFileUrl())
    val child1 = builder.addSampleEntity("c1")
    val child2 = builder.addSampleEntity("c2")
    builder.addEntity(ModifiableSampleEntityForSerialization::class.java, SampleEntityForSerializationSource("xx")) {
      parent = sampleEntity
      booleanProperty = true
      stringListProperty = mutableListOf("1", "2")
      stringMapProperty = mutableMapOf("1" to "2")
      children = listOf(child1, child2)
      fileProperty = tempFolder.toVirtualFileUrl()
      dataClasses = listOf(
        SampleDataClassForSerialization(tempFolder.toVirtualFileUrl()),
        SampleDataClassForSerialization(tempFolder.toVirtualFileUrl())
      )
    }

    verifySerializationRoundTrip(builder.toStorage())
  }

  @Test
  fun singletonEntitySource() {
    val builder = TypedEntityStorageBuilder.createProxy()
    builder.addSampleEntity("c2", source = SingletonEntitySource)
    verifySerializationRoundTrip(builder.toStorage())
  }

  object SingletonEntitySource : EntitySource
}
