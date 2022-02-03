// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryNameGenerator
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild
import com.intellij.workspaceModel.storage.impl.references.OneToOneChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.SerializationConstants
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

  override fun changeEntitySourcesToDirectoryBasedFormat(builder: WorkspaceEntityStorageBuilder, configLocation: JpsProjectConfigLocation) {
    /// XXX In fact, we suppose that all packaging element have a connection to the corresponding artifact.
    // However, technically, it's possible to create a packaging element without artifact or connection to another packaging element.
    // Here we could check that the amount of "processed" packaging elements equals to the amount of packaging elements in store,
    //   but unfortunately [WorkspaceModel.entities] function doesn't work with abstract entities at the moment.
    builder.entities(ArtifactEntity::class.java).forEach {
      // Convert artifact to the new source
      val artifactSource = JpsEntitySourceFactory.createJpsEntitySourceForArtifact(configLocation)
      builder.changeSource(it, artifactSource)

      // Convert it's packaging elements
      it.rootElement!!.forThisAndFullTree {
        builder.changeSource(it, artifactSource)
      }
    }

    // Convert properties
    builder.entities(ArtifactPropertiesEntity::class.java).forEach {
      builder.changeSource(it, it.artifact.entitySource)
    }
  }

  private fun PackagingElementEntity.forThisAndFullTree(action: (PackagingElementEntity) -> Unit) {
    action(this)
    if (this is CompositePackagingElementEntity) {
      this.children.forEach {
        if (it is CompositePackagingElementEntity) {
          it.forThisAndFullTree(action)
        }
        else {
          action(it)
        }
      }
    }
  }
}

private const val ARTIFACT_MANAGER_COMPONENT_NAME = "ArtifactManager"

internal class JpsArtifactsExternalFileSerializer(private val externalFile: JpsFileEntitySource.ExactFile,
                                                  private val internalArtifactsDirUrl: VirtualFileUrl,
                                                  private val fileInDirectorySourceNames: FileInDirectorySourceNames,
                                                  virtualFileManager: VirtualFileUrlManager)
  : JpsArtifactEntitiesSerializer(externalFile.file, externalFile, false, virtualFileManager), JpsFileEntityTypeSerializer<ArtifactEntity> {
  override val isExternalStorage: Boolean
    get() = true

  override val entityFilter: (ArtifactEntity) -> Boolean
    get() = { (it.entitySource as? JpsImportedEntitySource)?.storedExternally == true }

  override val additionalEntityTypes: List<Class<out WorkspaceEntity>>
    get() = listOf(ArtifactsOrderEntity::class.java)

  override fun createEntitySource(artifactTag: Element): EntitySource? {
    val externalSystemId = artifactTag.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE) ?: return null
    val artifactName = XmlSerializer.deserialize(artifactTag, ArtifactState::class.java).name
    val existingInternalSource = fileInDirectorySourceNames.findSource(mainEntityClass, artifactName)
    val internalEntitySource = if (existingInternalSource != null && existingInternalSource.directory == internalArtifactsDirUrl) {
      logger<JpsLibrariesExternalFileSerializer>().debug{ "Reuse existing source for artifact: ${existingInternalSource.fileNameId}=$artifactName" }
      existingInternalSource
    } else {
      JpsFileEntitySource.FileInDirectory(internalArtifactsDirUrl, externalFile.projectLocation)
    }
    return JpsImportedEntitySource(internalEntitySource, externalSystemId, true)
  }

  override fun getExternalSystemId(artifactEntity: ArtifactEntity): String? {
    val source = artifactEntity.entitySource
    return (source as? JpsImportedEntitySource)?.externalSystemId
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, ARTIFACT_MANAGER_COMPONENT_NAME, null)
  }
}

internal class JpsArtifactsFileSerializer(fileUrl: VirtualFileUrl, entitySource: JpsFileEntitySource, virtualFileManager: VirtualFileUrlManager)
  : JpsArtifactEntitiesSerializer(fileUrl, entitySource, true, virtualFileManager), JpsFileEntityTypeSerializer<ArtifactEntity> {
  override val isExternalStorage: Boolean
    get() = false

  override val entityFilter: (ArtifactEntity) -> Boolean
    get() = { (it.entitySource as? JpsImportedEntitySource)?.storedExternally != true }

  override val additionalEntityTypes: List<Class<out WorkspaceEntity>>
    get() = listOf(ArtifactsOrderEntity::class.java)

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, ARTIFACT_MANAGER_COMPONENT_NAME, null)
  }
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
  open val isExternalStorage: Boolean
    get() = false

  override val mainEntityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override fun loadEntities(builder: WorkspaceEntityStorageBuilder,
                            reader: JpsFileContentReader,
                            errorReporter: ErrorReporter,
                            virtualFileManager: VirtualFileUrlManager) {
    val artifactListElement = reader.loadComponent(fileUrl.url, ARTIFACT_MANAGER_COMPONENT_NAME)
    if (artifactListElement == null) return

    val orderOfItems = ArrayList<String>()
    artifactListElement.getChildren("artifact").forEach { actifactElement ->
      val entitySource = createEntitySource(actifactElement) ?: return@forEach
      val state = XmlSerializer.deserialize(actifactElement, ArtifactState::class.java)
      val outputUrl = state.outputPath?.let { path -> if (path.isNotEmpty()) virtualFileManager.fromPath(path) else null }
      val rootElement = loadPackagingElement(state.rootElement, entitySource, builder)
      val artifactEntity = builder.addArtifactEntity(state.name, state.artifactType, state.isBuildOnMake, outputUrl,
                                                     rootElement as CompositePackagingElementEntity, entitySource)
      for (propertiesState in state.propertiesList) {
        builder.addArtifactPropertiesEntity(artifactEntity, propertiesState.id, JDOMUtil.write(propertiesState.options), entitySource)
      }
      val externalSystemId = actifactElement.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE)
      if (externalSystemId != null && !isExternalStorage) {
        builder.addEntity(ModifiableArtifactExternalSystemIdEntity::class.java, entitySource) {
          this.externalSystemId = externalSystemId
          artifact = artifactEntity
        }
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
        builder.addEntity(ModifiableArtifactsOrderEntity::class.java, internalEntitySource) {
          orderOfArtifacts = orderOfItems
        }
      }
    }

  }

  protected open fun createEntitySource(artifactTag: Element): EntitySource? = internalEntitySource

  protected open fun getExternalSystemId(artifactEntity: ArtifactEntity): String? {
    return artifactEntity.externalSystemId?.externalSystemId
  }

  private fun loadPackagingElement(element: Element,
                                   source: EntitySource,
                                   builder: WorkspaceEntityStorageBuilder): PackagingElementEntity {
    fun loadElementChildren() = element.children.mapTo(ArrayList()) { loadPackagingElement(it, source, builder) }
    fun getAttribute(name: String) = element.getAttributeValue(name)!!
    fun getOptionalAttribute(name: String) = element.getAttributeValue(name)
    fun getPathAttribute(name: String) = element.getAttributeValue(name)!!.let { virtualFileManager.fromPath(it) }
    return when (val typeId = getAttribute("id")) {
      "root" -> builder.addArtifactRootElementEntity(loadElementChildren(), source)
      "directory" -> builder.addDirectoryPackagingElementEntity(getAttribute("name"), loadElementChildren(), source)
      "archive" -> builder.addArchivePackagingElementEntity(getAttribute("name"), loadElementChildren(), source)
      "dir-copy" -> builder.addDirectoryCopyPackagingElementEntity(getPathAttribute("path"), source)
      "file-copy" -> builder.addFileCopyPackagingElementEntity(getPathAttribute("path"), getOptionalAttribute("output-file-name"), source)
      "extracted-dir" -> builder.addExtractedDirectoryPackagingElementEntity(getPathAttribute("path"), getAttribute("path-in-jar"), source)
      "artifact" -> builder.addArtifactOutputPackagingElementEntity(getOptionalAttribute("artifact-name")?.let { ArtifactId(it) }, source)
      "module-output" -> builder.addModuleOutputPackagingElementEntity(getOptionalAttribute("name")?.let { ModuleId(it) }, source)
      "module-test-output" -> builder.addModuleTestOutputPackagingElementEntity(getOptionalAttribute("name")?.let { ModuleId(it) }, source)
      "module-source" -> builder.addModuleSourcePackagingElementEntity(getOptionalAttribute("name")?.let { ModuleId(it) }, source)
      "library" -> {
        val level = getOptionalAttribute("level")
        val name = getOptionalAttribute("name")
        if (level != null && name != null) {
          val moduleName = getOptionalAttribute("module-name")
          val parentId = when {
            moduleName != null -> LibraryTableId.ModuleLibraryTableId(ModuleId(moduleName))
            else -> LibraryNameGenerator.getLibraryTableId(level)
          }
          builder.addLibraryFilesPackagingElementEntity(LibraryId(name, parentId), source)
        }
        else {
          builder.addLibraryFilesPackagingElementEntity(null, source)
        }
      }
      else -> {
        val cloned = element.clone()
        cloned.removeContent()
        builder.addCustomPackagingElementEntity(typeId, JDOMUtil.write(cloned), loadElementChildren(), source)
      }
    }
  }

  override fun saveEntities(mainEntities: Collection<ArtifactEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: WorkspaceEntityStorage,
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
    artifactState.outputPath = JpsPathUtil.urlToPath(artifact.outputUrl?.url)
    val customProperties = artifact.customProperties.filter { it.entitySource == artifact.entitySource }
    artifactState.propertiesList = customProperties.mapNotNullTo(ArrayList()) {
      if (it.propertiesXmlTag == null) return@mapNotNullTo null
      ArtifactPropertiesState().apply {
        id = it.providerType
        options = it.propertiesXmlTag?.let { JDOMUtil.load(it) }
      }
    }
    artifactState.rootElement = savePackagingElement(artifact.rootElement!!)
    val externalSystemId = getExternalSystemId(artifact)
    if (externalSystemId != null) {
      if (isExternalStorage)
        artifactState.externalSystemId = externalSystemId
      else
        artifactState.externalSystemIdInInternalStorage = externalSystemId
    }
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
        setPathAttribute("path", element.filePath)
      }
      is FileCopyPackagingElementEntity -> {
        setId("file-copy")
        setPathAttribute("path", element.filePath)
        element.renamedOutputFileName?.let { setAttribute("output-file-name", it) }
      }
      is ExtractedDirectoryPackagingElementEntity -> {
        setId("extracted-dir")
        setPathAttribute("path", element.filePath)
        setAttribute("path-in-jar", element.pathInArchive)
      }
      is ArtifactOutputPackagingElementEntity -> {
        setId("artifact")
        element.artifact?.let { setAttribute("artifact-name", it.name) }
      }
      is ModuleOutputPackagingElementEntity -> {
        setId("module-output")
        element.module?.let { setAttribute("name", it.name) }
      }
      is ModuleTestOutputPackagingElementEntity -> {
        setId("module-test-output")
        element.module?.let { setAttribute("name", it.name) }
      }
      is ModuleSourcePackagingElementEntity -> {
        setId("module-source")
        element.module?.let { setAttribute("name", it.name) }
      }
      is LibraryFilesPackagingElementEntity -> {
        setId("library")
        val library = element.library
        if (library != null) {
          val tableId = library.tableId
          setAttribute("level", tableId.level)
          setAttribute("name", library.name)
          if (tableId is LibraryTableId.ModuleLibraryTableId) {
            setAttribute("module-name", tableId.moduleId.name)
          }
        }
      }
      is CustomPackagingElementEntity -> {
        setId(element.typeId)
        val customElement = JDOMUtil.load(element.propertiesXmlTag)
        customElement.attributes.forEach { attribute -> setAttribute(attribute.name, attribute.value) }
        saveElementChildren(element)
      }
    }
    return tag
  }

  override fun toString(): String = "${javaClass.simpleName.substringAfterLast('.')}($fileUrl)"
}

/**
 * This property indicates that external-system-id attribute should be stored in artifact configuration file to avoid unnecessary modifications
 */
@Suppress("unused")
internal class ArtifactExternalSystemIdEntityData : WorkspaceEntityData<ArtifactExternalSystemIdEntity>() {
  lateinit var externalSystemId: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactExternalSystemIdEntity {
    return ArtifactExternalSystemIdEntity(externalSystemId).also { addMetaData(it, snapshot) }
  }
}

internal class ArtifactExternalSystemIdEntity(
  val externalSystemId: String
) : WorkspaceEntityBase() {
  val artifact: ArtifactEntity by OneToOneChild.NotNull(ArtifactEntity::class.java)
}

internal class ModifiableArtifactExternalSystemIdEntity : ModifiableWorkspaceEntityBase<ArtifactExternalSystemIdEntity>() {
  var externalSystemId: String by EntityDataDelegation()
  var artifact: ArtifactEntity by MutableOneToOneChild.NotNull(ArtifactExternalSystemIdEntity::class.java, ArtifactEntity::class.java)
}

private val ArtifactEntity.externalSystemId get() = referrers(ArtifactExternalSystemIdEntity::artifact).firstOrNull()
