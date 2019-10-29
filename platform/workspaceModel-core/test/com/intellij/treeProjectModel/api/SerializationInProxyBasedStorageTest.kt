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
  val children: List<SampleEntity>
  val fileProperty: VirtualFileUrl
  val dataClasses: List<SampleDataClassForSerialization>
}

internal interface ModifiableSampleEntityForSerialization : SampleEntityForSerialization, ModifiableTypedEntity<SampleEntityForSerialization> {
  override var booleanProperty: Boolean
  override var stringProperty: String
  override var stringListProperty: MutableList<String>
  override var fileProperty: VirtualFileUrl
  override var parent: SampleEntity
  override var dataClasses: List<SampleDataClassForSerialization>
  override var children: List<SampleEntity>
}

internal data class SampleEntityForSerializationSource(val name: String) : EntitySource

class SerializationInProxyBasedStorageTest {
  @Rule @JvmField val tempDir = TempDirectory()

  private val serializer = KryoEntityStorageSerializer(TestEntityTypesResolver())

  @Test
  fun empty() {
    verifySerializationRoundTrip(TypedEntityStorageBuilder.create().toStorage(), serializer)
  }

  @Test
  fun smoke() {
    val tempFolder = tempDir.newFolder()

    val builder = TypedEntityStorageBuilder.create()
    val sampleEntity = builder.addSampleEntity("ggg", SampleEntitySource("y"), true, mutableListOf("5", "6"), tempFolder.toVirtualFileUrl())
    val child1 = builder.addSampleEntity("c1")
    val child2 = builder.addSampleEntity("c2")
    builder.addEntity(ModifiableSampleEntityForSerialization::class.java, SampleEntityForSerializationSource("xx")) {
      parent = sampleEntity
      booleanProperty = true
      stringListProperty = mutableListOf("1", "2")
      children = listOf(child1, child2)
      fileProperty = tempFolder.toVirtualFileUrl()
      dataClasses = listOf(
        SampleDataClassForSerialization(tempFolder.toVirtualFileUrl()),
        SampleDataClassForSerialization(tempFolder.toVirtualFileUrl())
      )
    }

    verifySerializationRoundTrip(builder.toStorage(), serializer)
  }

  @Test
  fun singletonEntitySource() {
    val builder = TypedEntityStorageBuilder.create()
    builder.addSampleEntity("c2", source = SingletonEntitySource)
    verifySerializationRoundTrip(builder.toStorage(), serializer)
  }

  object SingletonEntitySource : EntitySource
}
