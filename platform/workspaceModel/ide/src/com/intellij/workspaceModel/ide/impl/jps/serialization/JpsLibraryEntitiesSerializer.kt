// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.SerializationConstants
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

  override fun createSerializer(fileUrl: String,
                                entitySource: JpsFileEntitySource.FileInDirectory,
                                virtualFileManager: VirtualFileUrlManager): JpsFileEntitiesSerializer<LibraryEntity> {
    return JpsLibraryEntitiesSerializer(virtualFileManager.fromUrl(fileUrl), entitySource, LibraryTableId.ProjectLibraryTableId)
  }
}

private const val LIBRARY_TABLE_COMPONENT_NAME = "libraryTable"

internal class JpsLibrariesFileSerializer(entitySource: JpsFileEntitySource.ExactFile, libraryTableId: LibraryTableId)
  : JpsLibraryEntitiesSerializer(entitySource.file, entitySource, libraryTableId), JpsFileEntityTypeSerializer<LibraryEntity> {
  override val isExternalStorage: Boolean
    get() = false
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == libraryTableId && (it.entitySource as? JpsImportedEntitySource)?.storedExternally != true }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, LIBRARY_TABLE_COMPONENT_NAME, null)
  }
}

internal class JpsLibrariesExternalFileSerializer(private val externalFile: JpsFileEntitySource.ExactFile,
                                                  private val internalLibrariesDirUrl: VirtualFileUrl)
  : JpsLibraryEntitiesSerializer(externalFile.file, externalFile, LibraryTableId.ProjectLibraryTableId), JpsFileEntityTypeSerializer<LibraryEntity> {
  override val isExternalStorage: Boolean
    get() = true
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == LibraryTableId.ProjectLibraryTableId && (it.entitySource as? JpsImportedEntitySource)?.storedExternally == true }

  override fun createEntitySource(libraryTag: Element): EntitySource? {
    val externalSystemId = libraryTag.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE) ?: return null
    val internalEntitySource = JpsFileEntitySource.FileInDirectory(internalLibrariesDirUrl, externalFile.projectLocation)
    return JpsImportedEntitySource(internalEntitySource, externalSystemId, true)
  }

  override fun getExternalSystemId(libraryEntity: LibraryEntity): String? {
    val source = libraryEntity.entitySource
    return (source as? JpsImportedEntitySource)?.externalSystemId
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, LIBRARY_TABLE_COMPONENT_NAME, null)
  }
}

internal open class JpsLibraryEntitiesSerializer(override val fileUrl: VirtualFileUrl, override val internalEntitySource: JpsFileEntitySource,
                                                 protected val libraryTableId: LibraryTableId) : JpsFileEntitiesSerializer<LibraryEntity> {
  override val mainEntityClass: Class<LibraryEntity>
    get() = LibraryEntity::class.java

  override fun loadEntities(builder: WorkspaceEntityStorageBuilder,
                            reader: JpsFileContentReader, errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager) {
    val libraryTableTag = reader.loadComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME) ?: return
    for (libraryTag in libraryTableTag.getChildren(LIBRARY_TAG)) {
      val source = createEntitySource(libraryTag) ?: continue
      val name = libraryTag.getAttributeValueStrict(JpsModuleRootModelSerializer.NAME_ATTRIBUTE)

      val libraryId = LibraryId(name, libraryTableId)
      val existingLibraryEntity = builder.resolve(libraryId)
      if (existingLibraryEntity != null) {
        logger<JpsLibraryEntitiesSerializer>().error("""Error during entities loading
          |Entity with this library id already exists.
          |Library id: $libraryId
          |fileUrl: ${fileUrl.presentableUrl}
          |library table id: $libraryTableId
          |internal entity source: $internalEntitySource
        """.trimMargin())
      }

      loadLibrary(name, libraryTag, libraryTableId, builder, source, virtualFileManager)
    }
  }

  protected open fun createEntitySource(libraryTag: Element): EntitySource? = internalEntitySource

  override fun saveEntities(mainEntities: Collection<LibraryEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: WorkspaceEntityStorage,
                            writer: JpsFileContentWriter) {
    if (mainEntities.isEmpty()) return

    val componentTag = JDomSerializationUtil.createComponentElement(LIBRARY_TABLE_COMPONENT_NAME)
    mainEntities.sortedBy { it.name }.forEach {
      componentTag.addContent(saveLibrary(it, getExternalSystemId(it)))
    }
    writer.saveComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME, componentTag)
  }

  protected open fun getExternalSystemId(libraryEntity: LibraryEntity): String? = null

  override fun toString(): String = "${javaClass.simpleName.substringAfterLast('.')}($fileUrl)"
}

private const val DEFAULT_JAR_DIRECTORY_TYPE = "CLASSES"

internal fun loadLibrary(name: String, libraryElement: Element, libraryTableId: LibraryTableId, builder: WorkspaceEntityStorageBuilder,
                         source: EntitySource, virtualFileManager: VirtualFileUrlManager): LibraryEntity {
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
          .map { virtualFileManager.fromUrl(it) }
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
          roots.add(LibraryRoot(virtualFileManager.fromUrl(url), libraryRootTypes[rootType]!!, inclusionOptions))
        }
      }
    }
  }
  val libraryEntity = builder.addLibraryEntity(name, libraryTableId, roots, excludedRoots, source)
  if (type != null) {
    builder.addLibraryPropertiesEntity(libraryEntity, type, properties)
  }
  return libraryEntity
}

private val libraryRootTypes = ConcurrentFactoryMap.createMap<String, LibraryRootTypeId> { LibraryRootTypeId(it) }

internal fun saveLibrary(library: LibraryEntity, externalSystemId: String?): Element {
  val libraryTag = Element(LIBRARY_TAG)
  val legacyName = getLegacyLibraryName(library.persistentId())
  if (legacyName != null) {
    libraryTag.setAttribute(NAME_ATTRIBUTE, legacyName)
  }
  val customProperties = library.getCustomProperties()
  if (customProperties != null) {
    libraryTag.setAttribute(TYPE_ATTRIBUTE, customProperties.libraryType)
    val propertiesXmlTag = customProperties.propertiesXmlTag
    if (propertiesXmlTag != null) {
      libraryTag.addContent(JDOMUtil.load(propertiesXmlTag))
    }
  }
  if (externalSystemId != null) {
    libraryTag.setAttribute(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE, externalSystemId)
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

private val ROOT_TYPES_TO_WRITE_EMPTY_TAG = listOf("CLASSES", "SOURCES", "JAVADOC").map { libraryRootTypes[it]!! }
internal const val UNNAMED_LIBRARY_NAME_PREFIX = "#"
private const val UNIQUE_INDEX_LIBRARY_NAME_SUFFIX = "-d1a6f608-UNIQUE-INDEX-f29c-4df6-"

fun getLegacyLibraryName(libraryId: LibraryId): String? {
  if (libraryId.name.startsWith(UNNAMED_LIBRARY_NAME_PREFIX)) return null
  if (libraryId.name.contains(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)) return libraryId.name.substringBefore(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)
  return libraryId.name
}

fun generateLibraryEntityName(legacyLibraryName: String?, exists: (String) -> Boolean): String {
  if (legacyLibraryName == null) {
    var index = 1
    while (true) {
      val candidate = "$UNNAMED_LIBRARY_NAME_PREFIX$index"
      if (!exists(candidate)) {
        return candidate
      }

      index++
    }
    @Suppress("UNREACHABLE_CODE")
    error("Unable to suggest unique name for unnamed module library")
  }

  return generateUniqueLibraryName(legacyLibraryName, exists)
}

internal fun generateUniqueLibraryName(name: String, exists: (String) -> Boolean): String {
  if (!exists(name)) return name

  var index = 1
  while (true) {
    val candidate = "$name$UNIQUE_INDEX_LIBRARY_NAME_SUFFIX$index"
    if (!exists(candidate)) {
      return candidate
    }

    index++
  }
}
