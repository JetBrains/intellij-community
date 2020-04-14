package com.intellij.workspace.api

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import org.junit.Before
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
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun empty() {
    verifySerializationRoundTrip(TypedEntityStorageBuilder.createProxy().toStorage(), virtualFileManager)
  }

  @Test
  fun smoke() {
    val tempFolder = tempDir.newFolder()
    val virtualFileUrl = tempFolder.toVirtualFileUrl(virtualFileManager)
    val builder = TypedEntityStorageBuilder.createProxy()
    val sampleEntity = builder.addSampleEntity("ggg", SampleEntitySource("y"), true, mutableListOf("5", "6"), virtualFileManager, virtualFileUrl)
    val child1 = builder.addSampleEntity("c1", virtualFileManager = virtualFileManager)
    val child2 = builder.addSampleEntity("c2", virtualFileManager = virtualFileManager)
    builder.addEntity(ModifiableSampleEntityForSerialization::class.java, SampleEntityForSerializationSource("xx")) {
      parent = sampleEntity
      booleanProperty = true
      stringListProperty = mutableListOf("1", "2")
      stringMapProperty = mutableMapOf("1" to "2")
      children = listOf(child1, child2)
      fileProperty = virtualFileUrl
      dataClasses = listOf(
        SampleDataClassForSerialization(virtualFileUrl),
        SampleDataClassForSerialization(virtualFileUrl)
      )
    }

    verifySerializationRoundTrip(builder.toStorage(), virtualFileManager)
  }

  @Test
  fun singletonEntitySource() {
    val builder = TypedEntityStorageBuilder.createProxy()
    builder.addSampleEntity("c2", source = SingletonEntitySource, virtualFileManager = virtualFileManager)
    verifySerializationRoundTrip(builder.toStorage(), virtualFileManager)
  }

  object SingletonEntitySource : EntitySource
}
