// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.io.URLUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import io.opentelemetry.api.metrics.Meter
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
                                entitySource: JpsProjectFileEntitySource.FileInDirectory,
                                virtualFileManager: VirtualFileUrlManager): JpsArtifactEntitiesSerializer {
    return JpsArtifactEntitiesSerializer(virtualFileManager.getOrCreateFromUrl(fileUrl), entitySource, false, virtualFileManager)
  }

  override fun getDefaultFileName(entity: ArtifactEntity): String {
    return entity.name
  }

  override fun changeEntitySourcesToDirectoryBasedFormat(builder: MutableEntityStorage, configLocation: JpsProjectConfigLocation) {
    /// XXX In fact, we suppose that all packaging element have a connection to the corresponding artifact.
    // However, technically, it's possible to create a packaging element without artifact or connection to another packaging element.
    // Here we could check that the amount of "processed" packaging elements equals to the amount of packaging elements in store,
    //   but unfortunately [WorkspaceModel.entities] function doesn't work with abstract entities at the moment.
    builder.entities(ArtifactEntity::class.java).forEach {
      // Convert artifact to the new source
      val artifactSource = JpsEntitySourceFactory.createJpsEntitySourceForArtifact(configLocation)
      builder.modifyArtifactEntity(it) {
        this.entitySource = artifactSource
      }

      // Convert it's packaging elements
      it.rootElement!!.forThisAndFullTree {
        builder.modifyEntity(PackagingElementEntity.Builder::class.java, it) {
          this.entitySource = artifactSource
        }
      }
    }

    // Convert properties
    builder.entities(ArtifactPropertiesEntity::class.java).forEach {
      builder.modifyArtifactPropertiesEntity(it) {
        this.entitySource = it.artifact.entitySource
      }
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

internal class JpsArtifactsExternalFileSerializer(private val externalFile: JpsProjectFileEntitySource.ExactFile,
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
      logger<JpsLibrariesExternalFileSerializer>().debug { "Reuse existing source for artifact: ${existingInternalSource.fileNameId}=$artifactName" }
      existingInternalSource
    }
    else {
      JpsProjectFileEntitySource.FileInDirectory(internalArtifactsDirUrl, externalFile.projectLocation)
    }
    return JpsImportedEntitySource(internalEntitySource, externalSystemId, true)
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, ARTIFACT_MANAGER_COMPONENT_NAME, null)
  }
}

internal class JpsArtifactsFileSerializer(fileUrl: VirtualFileUrl,
                                          entitySource: JpsFileEntitySource,
                                          virtualFileManager: VirtualFileUrlManager)
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

internal open class JpsArtifactEntitiesSerializer(override val fileUrl: VirtualFileUrl,
                                                  override val internalEntitySource: JpsFileEntitySource,
                                                  private val preserveOrder: Boolean,
                                                  private val virtualFileManager: VirtualFileUrlManager) : JpsFileEntitiesSerializer<ArtifactEntity> {
  open val isExternalStorage: Boolean
    get() = false

  override val mainEntityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override fun loadEntities(
    reader: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager
  ): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>> = loadEntitiesTimeMs.addMeasuredTime {
    val artifactListElement = runCatchingXmlIssues { reader.loadComponent(fileUrl.url, ARTIFACT_MANAGER_COMPONENT_NAME) }
      .onFailure { return@addMeasuredTime LoadingResult(emptyMap(), it) }
      .getOrThrow()
    if (artifactListElement == null) return@addMeasuredTime LoadingResult(emptyMap(), null)

    val orderOfItems = ArrayList<String>()
    val artifactEntities = runCatchingXmlIssues { artifactListElement.getChildren("artifact") }
      .onFailure { return@addMeasuredTime LoadingResult(emptyMap(), it) }
      .getOrThrow()
      .mapNotNull { artifactElement ->
        runCatchingXmlIssues {
          val entitySource = createEntitySource(artifactElement) ?: return@mapNotNull null
          val state = XmlSerializer.deserialize(artifactElement, ArtifactState::class.java)
          val outputUrl = state.outputPath?.let { path ->
            if (path.isNotEmpty()) {
              virtualFileManager.getOrCreateFromUrl(path.toPathWithScheme())
            }
            else null
          }
          val rootElement = loadPackagingElement(state.rootElement, entitySource)
          val artifactEntity = ArtifactEntity(state.name, state.artifactType, state.isBuildOnMake, entitySource) {
            this.outputUrl = outputUrl
            this.rootElement = rootElement as CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>
          }
          for (propertiesState in state.propertiesList) {
            ArtifactPropertiesEntity(propertiesState.id, entitySource) {
              this.artifact = artifactEntity
              this.propertiesXmlTag = JDOMUtil.write(propertiesState.options)
            }
          }
          orderOfItems += state.name
          artifactEntity
        }
      }

    return@addMeasuredTime LoadingResult(
      mapOf(
        ArtifactsOrderEntity::class.java to listOf(ArtifactsOrderEntity(orderOfItems, internalEntitySource)),
        ArtifactEntity::class.java to artifactEntities.mapNotNull { it.getOrNull() },
      ),
      artifactEntities.firstOrNull { it.isFailure }?.exceptionOrNull())
  }

  override fun checkAndAddToBuilder(builder: MutableEntityStorage,
                                    orphanage: MutableEntityStorage,
                                    newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>) {
    if (preserveOrder) {
      val order = newEntities[ArtifactsOrderEntity::class.java]?.singleOrNull() as? ArtifactsOrderEntity.Builder
      if (order != null) {
        val entity = builder.entities(ArtifactsOrderEntity::class.java).firstOrNull()
        if (entity != null) {
          builder.modifyArtifactsOrderEntity(entity) {
            orderOfArtifacts.addAll(order.orderOfArtifacts)
          }
        }
        else {
          builder.addEntity(order)
        }
      }
    }
    newEntities.forEach { (key, value) ->
      if (key == ArtifactsOrderEntity::class.java) return@forEach

      value.forEach { builder.addEntity(it) }
    }
  }

  protected open fun createEntitySource(artifactTag: Element): EntitySource? {
    val externalSystemId = artifactTag.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE)
    return if (externalSystemId == null) internalEntitySource else JpsImportedEntitySource(internalEntitySource, externalSystemId, false)
  }

  private fun loadPackagingElement(element: Element,
                                   source: EntitySource): PackagingElementEntity.Builder<out PackagingElementEntity> {
    fun loadElementChildren() = element.children.mapTo(ArrayList()) { loadPackagingElement(it, source) }
    fun getAttribute(name: String) = element.getAttributeValue(name)!!
    fun getOptionalAttribute(name: String) = element.getAttributeValue(name)
    fun getPathAttribute(name: String) = element.getAttributeValue(name)!!.let { virtualFileManager.getOrCreateFromUrl(it.toPathWithScheme()) }
    return when (val typeId = getAttribute("id")) {
      "root" -> ArtifactRootElementEntity(source) {
        this.children = loadElementChildren()
      }
      "directory" -> DirectoryPackagingElementEntity(getAttribute("name"), source) {
        this.children = loadElementChildren()
      }
      "archive" -> ArchivePackagingElementEntity(getAttribute("name"), source) {
        this.children = loadElementChildren()
      }
      "dir-copy" -> DirectoryCopyPackagingElementEntity(getPathAttribute("path"), source)
      "file-copy" -> FileCopyPackagingElementEntity(getPathAttribute("path"), source) {
        this.renamedOutputFileName = getOptionalAttribute("output-file-name")
      }
      "extracted-dir" -> ExtractedDirectoryPackagingElementEntity(getPathAttribute("path"), getAttribute("path-in-jar"),
                                                                  source)
      "artifact" -> ArtifactOutputPackagingElementEntity(source) {
        this.artifact = getOptionalAttribute("artifact-name")?.let { ArtifactId(it) }
      }
      "module-output" -> ModuleOutputPackagingElementEntity(source) {
        this.module = getOptionalAttribute("name")?.let { ModuleId(it) }
      }
      "module-test-output" -> ModuleTestOutputPackagingElementEntity(source) {
        this.module = getOptionalAttribute("name")?.let { ModuleId(it) }
      }
      "module-source" -> ModuleSourcePackagingElementEntity(source) {
        this.module = getOptionalAttribute("name")?.let { ModuleId(it) }
      }
      "library" -> {
        val level = getOptionalAttribute("level")
        val name = getOptionalAttribute("name")
        if (level != null && name != null) {
          val moduleName = getOptionalAttribute("module-name")
          val parentId = when {
            moduleName != null -> LibraryTableId.ModuleLibraryTableId(ModuleId(moduleName))
            else -> LibraryNameGenerator.getLibraryTableId(level)
          }
          LibraryFilesPackagingElementEntity(source) {
            this.library = LibraryId(name, parentId)
          }
        }
        else {
          LibraryFilesPackagingElementEntity(source)
        }
      }
      else -> {
        val cloned = element.clone()
        cloned.removeContent()
        CustomPackagingElementEntity(typeId, JDOMUtil.write(cloned), source) {
          this.children = loadElementChildren()
        }
      }
    }
  }

  override fun saveEntities(mainEntities: Collection<ArtifactEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage,
                            writer: JpsFileContentWriter) = saveEntitiesTimeMs.addMeasuredTime {
    if (mainEntities.isEmpty()) return@addMeasuredTime

    val componentTag = JDomSerializationUtil.createComponentElement(ARTIFACT_MANAGER_COMPONENT_NAME)
    val orderOfItems = if (preserveOrder) (entities[ArtifactsOrderEntity::class.java]?.firstOrNull() as? ArtifactsOrderEntity?)?.orderOfArtifacts else null
    val sorted = sortByOrderEntity(orderOfItems, mainEntities.groupByTo(HashMap()) { it.name }.toMutableMap())
    sorted.forEach {
      componentTag.addContent(saveArtifact(it))
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
    val externalSystemId = (artifact.entitySource as? JpsImportedEntitySource)?.externalSystemId
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

  companion object {
    private val loadEntitiesTimeMs = MillisecondsMeasurer()
    private val saveEntitiesTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadEntitiesTimeCounter = meter.counterBuilder("jps.artifact.entities.serializer.load.entities.ms").buildObserver()
      val saveEntitiesTimeCounter = meter.counterBuilder("jps.artifact.entities.serializer.save.entities.ms").buildObserver()

      meter.batchCallback(
        {
          loadEntitiesTimeCounter.record(loadEntitiesTimeMs.asMilliseconds())
          saveEntitiesTimeCounter.record(saveEntitiesTimeMs.asMilliseconds())
        },
        loadEntitiesTimeCounter, saveEntitiesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
    }
  }

  private fun String.toPathWithScheme(): String {
    return URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(this)
  }
}