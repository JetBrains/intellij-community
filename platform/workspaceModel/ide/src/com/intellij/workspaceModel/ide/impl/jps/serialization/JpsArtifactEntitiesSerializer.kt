// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.levelToLibraryTableId
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState
import org.jetbrains.jps.model.serialization.artifact.ArtifactState
import org.jetbrains.jps.util.JpsPathUtil

internal class JpsArtifactsDirectorySerializerFactory(override val directoryUrl: String) : JpsDirectoryEntitiesSerializerFactory<ArtifactEntity> {
  override val componentName: String
    get() = ARTIFACT_MANAGER_COMPONENT_NAME
  override val entityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override fun createSerializer(fileUrl: String,
                                entitySource: JpsFileEntitySource.FileInDirectory,
                                virtualFileManager: VirtualFileUrlManager): JpsArtifactEntitiesSerializer {
    return JpsArtifactEntitiesSerializer(virtualFileManager.fromUrl(fileUrl), entitySource, false, virtualFileManager)
  }

  override fun getDefaultFileName(entity: ArtifactEntity): String {
    return entity.name
  }
}

private const val ARTIFACT_MANAGER_COMPONENT_NAME = "ArtifactManager"

internal class JpsArtifactsFileSerializer(fileUrl: VirtualFileUrl, entitySource: JpsFileEntitySource, virtualFileManager: VirtualFileUrlManager)
  : JpsArtifactEntitiesSerializer(fileUrl, entitySource, true, virtualFileManager), JpsFileEntityTypeSerializer<ArtifactEntity> {
  override val isExternalStorage: Boolean
    get() = false
  override val additionalEntityTypes: List<Class<out WorkspaceEntity>>
    get() = listOf(ArtifactsOrderEntity::class.java)
}

/**
 * This entity stores order of artifacts in ipr file. This is needed to ensure that artifact tags are saved in the same order to avoid
 * unnecessary modifications of ipr file.
 */
@Suppress("unused")
internal class ArtifactsOrderEntityData : WorkspaceEntityData<ArtifactsOrderEntity>() {
  lateinit var orderOfArtifacts: List<String>
  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactsOrderEntity {
    return ArtifactsOrderEntity(orderOfArtifacts).also { addMetaData(it, snapshot) }
  }
}

internal class ArtifactsOrderEntity(
  val orderOfArtifacts: List<String>
) : WorkspaceEntityBase()

internal class ModifiableArtifactsOrderEntity : ModifiableWorkspaceEntityBase<ArtifactsOrderEntity>()  {
  var orderOfArtifacts: List<String> by EntityDataDelegation()
}

internal open class JpsArtifactEntitiesSerializer(override val fileUrl: VirtualFileUrl,
                                                  override val internalEntitySource: JpsFileEntitySource,
                                                  private val preserveOrder: Boolean,
                                                  private val virtualFileManager: VirtualFileUrlManager) : JpsFileEntitiesSerializer<ArtifactEntity> {
  override val mainEntityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override fun loadEntities(builder: WorkspaceEntityStorageBuilder, reader: JpsFileContentReader, virtualFileManager: VirtualFileUrlManager) {
    val artifactListElement = reader.loadComponent(fileUrl.url, ARTIFACT_MANAGER_COMPONENT_NAME)
    if (artifactListElement == null) return

    val source = internalEntitySource
    val orderOfItems = ArrayList<String>()
    artifactListElement.getChildren("artifact").forEach {
      val state = XmlSerializer.deserialize(it, ArtifactState::class.java)
      val outputUrl = virtualFileManager.fromPath(state.outputPath)
      val rootElement = loadPackagingElement(state.rootElement, source, builder)
      val artifactEntity = builder.addArtifactEntity(state.name, state.artifactType, state.isBuildOnMake, outputUrl,
                                                     rootElement as CompositePackagingElementEntity, source)
      for (propertiesState in state.propertiesList) {
        builder.addArtifactPropertiesEntity(artifactEntity, propertiesState.id, JDOMUtil.write(propertiesState.options), source)
      }
      orderOfItems += state.name
    }
    if (preserveOrder) {
      val entity = builder.entities(ArtifactsOrderEntity::class.java).firstOrNull()
      if (entity != null) {
        builder.modifyEntity(ModifiableArtifactsOrderEntity::class.java, entity) {
          orderOfArtifacts = orderOfItems
        }
      }
      else {
        builder.addEntity(ModifiableArtifactsOrderEntity::class.java, source) {
          orderOfArtifacts = orderOfItems
        }
      }
    }
  }

  private fun loadPackagingElement(element: Element,
                                   source: EntitySource,
                                   builder: WorkspaceEntityStorageBuilder): PackagingElementEntity {
    fun loadElementChildren() = element.children.mapTo(ArrayList()) { loadPackagingElement(it, source, builder) }
    fun getAttribute(name: String) = element.getAttributeValue(name)!!
    fun getOptionalAttribute(name: String) = element.getAttributeValue(name)
    fun getPathAttribute(name: String) = virtualFileManager.fromPath(element.getAttributeValue(name)!!)
    return when (val typeId = getAttribute("id")) {
      "root" -> builder.addArtifactRootElementEntity(loadElementChildren(), source)
      "directory" -> builder.addDirectoryPackagingElementEntity(getAttribute("name"), loadElementChildren(), source)
      "archive" -> builder.addArchivePackagingElementEntity(getAttribute("name"), loadElementChildren(), source)
      "dir-copy" -> builder.addDirectoryCopyPackagingElementEntity(getPathAttribute("path"), source)
      "file-copy" -> builder.addFileCopyPackagingElementEntity(getPathAttribute("path"), getOptionalAttribute("output-file-name"), source)
      "extracted-dir" -> builder.addExtractedDirectoryPackagingElementEntity(getPathAttribute("path"), getAttribute("path-in-jar"), source)
      "artifact" -> builder.addArtifactOutputPackagingElementEntity(ArtifactId(getAttribute("artifact-name")), source)
      "module-output" -> builder.addModuleOutputPackagingElementEntity(ModuleId(getAttribute("name")), source)
      "module-test-output" -> builder.addModuleTestOutputPackagingElementEntity(ModuleId(getAttribute("name")), source)
      "module-source" -> builder.addModuleSourcePackagingElementEntity(ModuleId(getAttribute("name")), source)
      "library" -> {
        val moduleName = getOptionalAttribute("module-name")
        val level = getAttribute("level")
        val name = getOptionalAttribute("name")
        val parentId = when {
          moduleName != null -> LibraryTableId.ModuleLibraryTableId(ModuleId(moduleName))
          else -> levelToLibraryTableId(level)
        }
        builder.addLibraryFilesPackagingElementEntity(LibraryId(name!!, parentId), source)
      }
      else -> builder.addCustomPackagingElementEntity(typeId, JDOMUtil.write(element), source)
    }
  }

  override fun saveEntities(mainEntities: Collection<ArtifactEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            writer: JpsFileContentWriter) {
    if (mainEntities.isEmpty()) return

    val componentTag = JDomSerializationUtil.createComponentElement(ARTIFACT_MANAGER_COMPONENT_NAME)
    val artifactsByName = mainEntities.groupByTo(HashMap()) { it.name }
    val orderOfItems = if (preserveOrder) (entities[ArtifactsOrderEntity::class.java]?.firstOrNull() as? ArtifactsOrderEntity?)?.orderOfArtifacts else null
    orderOfItems?.forEach { name ->
      val artifacts = artifactsByName.remove(name)
      artifacts?.forEach {
        componentTag.addContent(saveArtifact(it))
      }
    }
    artifactsByName.values.forEach {
      it.forEach { artifact ->
        componentTag.addContent(saveArtifact(artifact))
      }
    }
    writer.saveComponent(fileUrl.url, ARTIFACT_MANAGER_COMPONENT_NAME, componentTag)
  }

  private fun saveArtifact(artifact: ArtifactEntity): Element {
    val artifactState = ArtifactState()
    artifactState.name = artifact.name
    artifactState.artifactType = artifact.artifactType
    artifactState.isBuildOnMake = artifact.includeInProjectBuild
    artifactState.outputPath = JpsPathUtil.urlToPath(artifact.outputUrl.url)
    val customProperties = artifact.customProperties.filter { it.entitySource == artifact.entitySource }
    artifactState.propertiesList = customProperties.mapTo(ArrayList()) {
      ArtifactPropertiesState().apply {
        id = it.providerType
        options = it.propertiesXmlTag?.let { JDOMUtil.load(it) }
      }
    }
    artifactState.rootElement = savePackagingElement(artifact.rootElement)
    return XmlSerializer.serialize(artifactState, SkipDefaultsSerializationFilter())
  }

  private fun savePackagingElement(element: PackagingElementEntity): Element {
    val tag = Element("element")

    fun setId(typeId: String) = tag.setAttribute("id", typeId)
    fun setAttribute(name: String, value: String) = tag.setAttribute(name, value)
    fun setPathAttribute(name: String, value: VirtualFileUrl) = tag.setAttribute(name, JpsPathUtil.urlToPath(value.url))

    fun saveElementChildren(composite: CompositePackagingElementEntity) {
      composite.children.forEach {
        tag.addContent(savePackagingElement(it))
      }
    }

    when (element) {
      is ArtifactRootElementEntity -> {
        setId("root")
        saveElementChildren(element)
      }
      is DirectoryPackagingElementEntity -> {
        setId("directory")
        setAttribute("name", element.directoryName)
        saveElementChildren(element)
      }
      is ArchivePackagingElementEntity -> {
        setId("archive")
        setAttribute("name", element.fileName)
        saveElementChildren(element)
      }
      is DirectoryCopyPackagingElementEntity -> {
        setId("dir-copy")
        setPathAttribute("path", element.directory)
      }
      is FileCopyPackagingElementEntity -> {
        setId("file-copy")
        setPathAttribute("path", element.file)
        element.renamedOutputFileName?.let { setAttribute("output-file-name", it) }
      }
      is ExtractedDirectoryPackagingElementEntity -> {
        setId("extracted-dir")
        setPathAttribute("path", element.archive)
        setAttribute("path-in-jar", element.pathInArchive)
      }
      is ArtifactOutputPackagingElementEntity -> {
        setId("artifact")
        setAttribute("artifact-name", element.artifact.name)
      }
      is ModuleOutputPackagingElementEntity -> {
        setId("module-output")
        setAttribute("name", element.module.name)
      }
      is ModuleTestOutputPackagingElementEntity -> {
        setId("module-test-output")
        setAttribute("name", element.module.name)
      }
      is ModuleSourcePackagingElementEntity -> {
        setId("module-source")
        setAttribute("name", element.module.name)
      }
      is LibraryFilesPackagingElementEntity -> {
        setId("library")
        val tableId = element.library.tableId
        setAttribute("level", tableId.level)
        setAttribute("name", element.library.name)
        if (tableId is LibraryTableId.ModuleLibraryTableId) {
          setAttribute("module-name", tableId.moduleId.name)
        }
      }
    }
    return tag
  }
}