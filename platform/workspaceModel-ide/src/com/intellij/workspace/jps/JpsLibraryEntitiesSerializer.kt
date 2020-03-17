package com.intellij.workspace.jps

import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

internal class JpsLibrariesDirectorySerializerFactory(override val directoryUrl: String) : JpsDirectoryEntitiesSerializerFactory<LibraryEntity> {
  override val componentName: String
    get() = LIBRARY_TABLE_COMPONENT_NAME

  override fun getDefaultFileName(entity: LibraryEntity): String {
    return entity.name
  }

  override val entityClass: Class<LibraryEntity>
    get() = LibraryEntity::class.java

  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == LibraryTableId.ProjectLibraryTableId }

  override fun createSerializer(fileUrl: String, entitySource: JpsFileEntitySource.FileInDirectory): JpsFileEntitiesSerializer<LibraryEntity> {
    return JpsLibraryEntitiesSerializer(VirtualFileUrlManager.fromUrl(fileUrl), entitySource, LibraryTableId.ProjectLibraryTableId)
  }
}

private const val LIBRARY_TABLE_COMPONENT_NAME = "libraryTable"

internal class JpsLibrariesFileSerializer(fileUrl: VirtualFileUrl, entitySource: JpsFileEntitySource, libraryTableId: LibraryTableId)
  : JpsLibraryEntitiesSerializer(fileUrl, entitySource, libraryTableId), JpsFileEntityTypeSerializer<LibraryEntity> {
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == libraryTableId }
}

internal open class JpsLibraryEntitiesSerializer(override val fileUrl: VirtualFileUrl, override val entitySource: JpsFileEntitySource,
                                                 protected val libraryTableId: LibraryTableId) : JpsFileEntitiesSerializer<LibraryEntity> {
  override val mainEntityClass: Class<LibraryEntity>
    get() = LibraryEntity::class.java

  override fun loadEntities(builder: TypedEntityStorageBuilder,
                            reader: JpsFileContentReader) {
    val source = entitySource
    val libraryTableTag = reader.loadComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME) ?: return
    for (libraryTag in libraryTableTag.getChildren(LIBRARY_TAG)) {
      val name = libraryTag.getAttributeValueStrict(JpsModuleRootModelSerializer.NAME_ATTRIBUTE)
      loadLibrary(name, libraryTag, libraryTableId, builder, source)
    }
  }

  override fun saveEntities(mainEntities: Collection<LibraryEntity>,
                            entities: Map<Class<out TypedEntity>, List<TypedEntity>>,
                            writer: JpsFileContentWriter): List<TypedEntity> {
    if (mainEntities.isEmpty()) {
      return emptyList()
    }

    val savedEntities = ArrayList<TypedEntity>()
    val componentTag = JDomSerializationUtil.createComponentElement(LIBRARY_TABLE_COMPONENT_NAME)
    mainEntities.sortedBy { it.name }.forEach {
      componentTag.addContent(saveLibrary(it, savedEntities))
    }
    writer.saveComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME, componentTag)
    return savedEntities
  }
}

private const val DEFAULT_JAR_DIRECTORY_TYPE = "CLASSES"

internal fun loadLibrary(name: String,
                         libraryElement: Element,
                         libraryTableId: LibraryTableId,
                         builder: TypedEntityStorageBuilder,
                         source: EntitySource): LibraryEntity {
  val roots = ArrayList<LibraryRoot>()
  val excludedRoots = ArrayList<VirtualFileUrl>()
  val jarDirectories = libraryElement.getChildren(JAR_DIRECTORY_TAG).associateBy(
    {
      Pair(it.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE) ?: DEFAULT_JAR_DIRECTORY_TYPE,
           it.getAttributeValueStrict(JpsModuleRootModelSerializer.URL_ATTRIBUTE))
    },
    {
      if (it.getAttributeValue(RECURSIVE_ATTRIBUTE)?.toBoolean() == true) LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY
      else LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT
    }
  )

  val type = libraryElement.getAttributeValue("type")
  var properties: String? = null
  for (childElement in libraryElement.children) {
    when (childElement.name) {
      "excluded" -> excludedRoots.addAll(
        childElement.getChildren(JpsJavaModelSerializerExtension.ROOT_TAG)
          .map { it.getAttributeValueStrict(JpsModuleRootModelSerializer.URL_ATTRIBUTE) }
          .map { VirtualFileUrlManager.fromUrl(it) }
      )
      PROPERTIES_TAG -> {
        properties = JDOMUtil.write(childElement)
      }
      JAR_DIRECTORY_TAG -> {
      }
      else -> {
        val rootType = childElement.name
        for (rootTag in childElement.getChildren(JpsJavaModelSerializerExtension.ROOT_TAG)) {
          val url = rootTag.getAttributeValueStrict(JpsModuleRootModelSerializer.URL_ATTRIBUTE)
          val inclusionOptions = jarDirectories[Pair(rootType, url)] ?: LibraryRoot.InclusionOptions.ROOT_ITSELF
          roots.add(LibraryRoot(VirtualFileUrlManager.fromUrl(url), LibraryRootTypeId(rootType), inclusionOptions))
        }
      }
    }
  }
  val libraryEntity = builder.addLibraryEntity(name, libraryTableId, roots, excludedRoots, source)
  if (type != null) {
    builder.addLibraryPropertiesEntity(libraryEntity, type, properties, source)
  }
  return libraryEntity
}

internal fun saveLibrary(library: LibraryEntity, savedEntities: MutableList<TypedEntity>): Element {
  savedEntities.add(library)
  val libraryTag = Element(LIBRARY_TAG)
  val legacyName = LegacyBridgeLibraryImpl.getLegacyLibraryName(library.persistentId())
  if (legacyName != null) {
    libraryTag.setAttribute(NAME_ATTRIBUTE, legacyName)
  }
  val customProperties = library.getCustomProperties()
  if (customProperties != null) {
    savedEntities.add(customProperties)
    libraryTag.setAttribute(TYPE_ATTRIBUTE, customProperties.libraryType)
    val propertiesXmlTag = customProperties.propertiesXmlTag
    if (propertiesXmlTag != null) {
      libraryTag.addContent(JDOMUtil.load(propertiesXmlTag))
    }
  }
  val rootsMap = library.roots.groupByTo(HashMap()) { it.type }
  ROOT_TYPES_TO_WRITE_EMPTY_TAG.forEach {
    rootsMap.putIfAbsent(it, ArrayList())
  }
  val jarDirectoriesTags = ArrayList<Element>()
  rootsMap.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {it.key.name}).forEach { (rootType, roots) ->
    val rootTypeTag = Element(rootType.name)
    roots.forEach {
      rootTypeTag.addContent(Element(ROOT_TAG).setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url.url))
    }
    roots.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {it.url.url}).forEach {
      if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
        val jarDirectoryTag = Element(JAR_DIRECTORY_TAG)
        jarDirectoryTag.setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url.url)
        jarDirectoryTag.setAttribute(RECURSIVE_ATTRIBUTE, (it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY).toString())
        if (rootType.name != DEFAULT_JAR_DIRECTORY_TYPE) {
          jarDirectoryTag.setAttribute(TYPE_ATTRIBUTE, rootType.name)
        }
        jarDirectoriesTags.add(jarDirectoryTag)
      }
    }
    libraryTag.addContent(rootTypeTag)
  }
  val excludedRoots = library.excludedRoots
  if (excludedRoots.isNotEmpty()) {
    val excludedTag = Element("excluded")
    excludedRoots.forEach {
      excludedTag.addContent(Element(ROOT_TAG).setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url))
    }
    libraryTag.addContent(excludedTag)
  }
  jarDirectoriesTags.forEach {
    libraryTag.addContent(it)
  }
  return libraryTag
}

private val ROOT_TYPES_TO_WRITE_EMPTY_TAG = listOf("CLASSES", "SOURCES", "JAVADOC").map { LibraryRootTypeId(it) }