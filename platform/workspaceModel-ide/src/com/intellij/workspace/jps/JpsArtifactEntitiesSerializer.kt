package com.intellij.workspace.jps

import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import com.intellij.workspace.legacyBridge.intellij.toLibraryTableId
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState
import org.jetbrains.jps.model.serialization.artifact.ArtifactState
import org.jetbrains.jps.util.JpsPathUtil

internal class JpsArtifactsDirectorySerializerFactory(override val directoryUrl: String,
                                                      private val storagePlace: JpsProjectStoragePlace.DirectoryBased) : JpsDirectoryEntitiesSerializerFactory<ArtifactEntity> {
  override val componentName: String
    get() = ARTIFACT_MANAGER_COMPONENT_NAME
  override val entityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override fun createSerializer(fileUrl: String): JpsFileEntitiesSerializer<ArtifactEntity> {
    return JpsArtifactEntitiesSerializer(fileUrl, storagePlace)
  }

  override fun getDefaultFileName(entity: ArtifactEntity): String {
    return entity.name
  }
}

private const val ARTIFACT_MANAGER_COMPONENT_NAME = "ArtifactManager"

internal class JpsArtifactsFileSerializer(fileUrl: String, storagePlace: JpsProjectStoragePlace) : JpsArtifactEntitiesSerializer(fileUrl, storagePlace),
                                                                                                   JpsFileEntityTypeSerializer<ArtifactEntity>

internal open class JpsArtifactEntitiesSerializer(private val fileUrl: String,
                                                  private val storagePlace: JpsProjectStoragePlace) : JpsFileEntitiesSerializer<ArtifactEntity> {
  override val mainEntityClass: Class<ArtifactEntity>
    get() = ArtifactEntity::class.java

  override val entitySource: JpsFileEntitySource
    get() = JpsFileEntitySource(VirtualFileUrlManager.fromUrl(fileUrl), storagePlace)

  override fun loadEntities(builder: TypedEntityStorageBuilder,
                            reader: JpsFileContentReader) {
    val artifactListElement = reader.loadComponent(fileUrl, ARTIFACT_MANAGER_COMPONENT_NAME)
    if (artifactListElement == null) return

    val source = entitySource
    artifactListElement.getChildren("artifact").forEach {
      val state = XmlSerializer.deserialize(it, ArtifactState::class.java)
      val outputUrl = VirtualFileUrlManager.fromUrl(JpsPathUtil.pathToUrl(state.outputPath))
      val rootElement = loadPackagingElement(state.rootElement, source, builder)
      val artifactEntity = builder.addArtifactEntity(state.name, state.artifactType, state.isBuildOnMake, outputUrl,
                                                     rootElement as CompositePackagingElementEntity, source)
      for (propertiesState in state.propertiesList) {
        builder.addArtifactPropertisEntity(artifactEntity, propertiesState.id, JDOMUtil.write(propertiesState.options), source)
      }
    }
  }

  private fun loadPackagingElement(element: Element,
                                   source: EntitySource,
                                   builder: TypedEntityStorageBuilder): PackagingElementEntity {
    fun loadElementChildren() = element.children.mapTo(ArrayList()) { loadPackagingElement(it, source, builder) }
    fun getAttribute(name: String) = element.getAttributeValue(name)!!
    fun getOptionalAttribute(name: String) = element.getAttributeValue(name)
    fun getPathAttribute(name: String) = VirtualFileUrlManager.fromUrl(JpsPathUtil.pathToUrl(element.getAttributeValue(name)!!))
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
          else -> toLibraryTableId(level)
        }
        builder.addLibraryFilesPackagingElementEntity(LibraryId(name!!, parentId), source)
      }
      else -> builder.addCustomPackagingElementEntity(typeId, JDOMUtil.write(element), source)
    }
  }

  override fun saveEntities(mainEntities: Collection<ArtifactEntity>,
                            entities: Map<Class<out TypedEntity>, List<TypedEntity>>,
                            writer: JpsFileContentWriter): List<TypedEntity> {
    if (mainEntities.isEmpty()) {
      return emptyList()
    }

    val componentTag = JDomSerializationUtil.createComponentElement(ARTIFACT_MANAGER_COMPONENT_NAME)
    val savedEntities = ArrayList<TypedEntity>()
    mainEntities.forEach {
      componentTag.addContent(saveArtifact(it, savedEntities))
    }
    writer.saveComponent(fileUrl, ARTIFACT_MANAGER_COMPONENT_NAME, componentTag)
    return savedEntities
  }

  private fun saveArtifact(artifact: ArtifactEntity, savedEntities: MutableList<TypedEntity>): Element {
    val artifactState = ArtifactState()
    artifactState.name = artifact.name
    artifactState.artifactType = artifact.artifactType
    artifactState.isBuildOnMake = artifact.includeInProjectBuild
    artifactState.outputPath = JpsPathUtil.urlToPath(artifact.outputUrl.url)
    savedEntities.add(artifact)
    val customProperties = artifact.customProperties.filter { it.entitySource == artifact.entitySource }
    savedEntities.addAll(customProperties)
    artifactState.propertiesList = customProperties.mapTo(ArrayList()) {
      ArtifactPropertiesState().apply {
        id = it.providerType
        options = it.propertiesXmlTag?.let { JDOMUtil.load(it) }
      }
    }
    artifactState.rootElement = savePackagingElement(artifact.rootElement, savedEntities)
    return XmlSerializer.serialize(artifactState, SkipDefaultsSerializationFilter())
  }

  private fun savePackagingElement(element: PackagingElementEntity, savedEntities: MutableList<TypedEntity>): Element {
    val tag = Element("element")

    fun setId(typeId: String) = tag.setAttribute("id", typeId)
    fun setAttribute(name: String, value: String) = tag.setAttribute(name, value)
    fun setPathAttribute(name: String, value: VirtualFileUrl) = tag.setAttribute(name, JpsPathUtil.urlToPath(value.url))

    fun saveElementChildren(composite: CompositePackagingElementEntity) {
      composite.children.forEach {
        savedEntities.add(it)
        tag.addContent(savePackagingElement(it, savedEntities))
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
    savedEntities.add(element)
    return tag
  }
}