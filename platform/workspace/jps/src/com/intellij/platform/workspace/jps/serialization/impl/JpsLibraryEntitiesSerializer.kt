// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.ConcurrentFactoryMap
import io.opentelemetry.api.metrics.Meter
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.util.concurrent.atomic.AtomicLong

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
                                entitySource: JpsProjectFileEntitySource.FileInDirectory,
                                virtualFileManager: VirtualFileUrlManager): JpsFileEntitiesSerializer<LibraryEntity> {
    return JpsLibraryEntitiesSerializer(virtualFileManager.fromUrl(fileUrl), entitySource, LibraryTableId.ProjectLibraryTableId)
  }

  override fun changeEntitySourcesToDirectoryBasedFormat(builder: MutableEntityStorage, configLocation: JpsProjectConfigLocation) {
    builder.entities(LibraryEntity::class.java).forEach {
      if (it.tableId == LibraryTableId.ProjectLibraryTableId) {
        builder.modifyEntity(it) {
          this.entitySource = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(configLocation)
        }
      }
    }
    builder.entities(LibraryPropertiesEntity::class.java).forEach {
      if (it.library.tableId == LibraryTableId.ProjectLibraryTableId) {
        builder.modifyEntity(it) {
          this.entitySource = it.library.entitySource
        }
      }
    }
  }
}

private const val LIBRARY_TABLE_COMPONENT_NAME = "libraryTable"

internal class JpsGlobalLibrariesFileSerializer(entitySource: JpsGlobalFileEntitySource)
  : JpsLibraryEntitiesSerializer(entitySource.file, entitySource,
                                 LibraryTableId.GlobalLibraryTableId(
                                   "application" /* equal to LibraryTablesRegistrar.APPLICATION_LEVEL */)),
    JpsFileEntityTypeSerializer<LibraryEntity> {
  override val isExternalStorage: Boolean
    get() = false

  /* Working only with global libraries omitting the custom one */
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == libraryTableId }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, LIBRARY_TABLE_COMPONENT_NAME, null)
  }
}


internal class JpsLibrariesFileSerializer(entitySource: JpsProjectFileEntitySource.ExactFile, libraryTableId: LibraryTableId)
  : JpsLibraryEntitiesSerializer(entitySource.file, entitySource, libraryTableId), JpsFileEntityTypeSerializer<LibraryEntity> {
  override val isExternalStorage: Boolean
    get() = false
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == libraryTableId && (it.entitySource as? JpsImportedEntitySource)?.storedExternally != true }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, LIBRARY_TABLE_COMPONENT_NAME, null)
  }
}

internal class JpsLibrariesExternalFileSerializer(private val externalFile: JpsProjectFileEntitySource.ExactFile,
                                                  private val internalLibrariesDirUrl: VirtualFileUrl,
                                                  private val fileInDirectorySourceNames: FileInDirectorySourceNames)
  : JpsLibraryEntitiesSerializer(externalFile.file, externalFile,
                                 LibraryTableId.ProjectLibraryTableId), JpsFileEntityTypeSerializer<LibraryEntity> {
  override val isExternalStorage: Boolean
    get() = true
  override val entityFilter: (LibraryEntity) -> Boolean
    get() = { it.tableId == LibraryTableId.ProjectLibraryTableId && (it.entitySource as? JpsImportedEntitySource)?.storedExternally == true }

  override fun createEntitySource(libraryTag: Element): EntitySource? {
    val externalSystemId = libraryTag.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE) ?: return null
    val libraryName = libraryTag.getAttributeValueStrict(JpsModuleRootModelSerializer.NAME_ATTRIBUTE)
    val existingInternalSource = fileInDirectorySourceNames.findSource(mainEntityClass, libraryName)
    val internalEntitySource = if (existingInternalSource != null && existingInternalSource.directory == internalLibrariesDirUrl) {
      logger<JpsLibrariesExternalFileSerializer>().debug { "Reuse existing source for library: ${existingInternalSource.fileNameId}=$libraryName" }
      existingInternalSource
    }
    else {
      JpsProjectFileEntitySource.FileInDirectory(internalLibrariesDirUrl, externalFile.projectLocation)
    }
    return JpsImportedEntitySource(internalEntitySource, externalSystemId, true)
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, LIBRARY_TABLE_COMPONENT_NAME, null)
  }
}

open class JpsLibraryEntitiesSerializer(override val fileUrl: VirtualFileUrl,
                                                 override val internalEntitySource: JpsFileEntitySource,
                                                 protected val libraryTableId: LibraryTableId) : JpsFileEntitiesSerializer<LibraryEntity> {
  open val isExternalStorage: Boolean
    get() = false

  override val mainEntityClass: Class<LibraryEntity>
    get() = LibraryEntity::class.java

  override fun loadEntities(
    reader: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager
  ): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>> = loadEntitiesTimeMs.addMeasuredTimeMillis {
    val libraryTableTag = runCatchingXmlIssues { reader.loadComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME) }
                            .onFailure { return@addMeasuredTimeMillis LoadingResult(emptyMap(), null) }
                            .getOrThrow() ?: return@addMeasuredTimeMillis LoadingResult(emptyMap(), null)
    val libs = runCatchingXmlIssues { libraryTableTag.getChildren(LIBRARY_TAG) }
      .onFailure { return@addMeasuredTimeMillis LoadingResult(emptyMap(), null) }
      .getOrThrow()
      .mapNotNull { libraryTag ->
        runCatchingXmlIssues {
          val source = createEntitySource(libraryTag) ?: return@mapNotNull null
          val name = libraryTag.getAttributeValueStrict(JpsModuleRootModelSerializer.NAME_ATTRIBUTE)
          loadLibrary(name, libraryTag, libraryTableId, source, virtualFileManager)
        }
      }

    return@addMeasuredTimeMillis LoadingResult(
      mapOf(LibraryEntity::class.java to libs.mapNotNull { it.getOrNull() }),
      libs.firstOrNull { it.isFailure }?.exceptionOrNull(),
    )
  }

  @Suppress("UNCHECKED_CAST")
  override fun checkAndAddToBuilder(builder: MutableEntityStorage,
                                    orphanage: MutableEntityStorage,
                                    newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>) {
    val libraries = (newEntities[LibraryEntity::class.java] as? List<LibraryEntity>) ?: emptyList()
    libraries.forEach {
      if (it.symbolicId in builder) {
        thisLogger().error("""Error during entities loading
            |Entity with this library id already exists.
            |Library id: ${it.symbolicId}
            |fileUrl: ${fileUrl.presentableUrl}
            |library table id: ${it.tableId}
            |internal entity source: ${internalEntitySource}
          """.trimMargin())
      }
    }

    newEntities.values.forEach { lists -> lists.forEach { builder addEntity it } }
  }

  protected open fun createEntitySource(libraryTag: Element): EntitySource? {
    val externalSystemId = libraryTag.getAttributeValue(SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE)
    return if (externalSystemId == null) internalEntitySource else JpsImportedEntitySource(internalEntitySource, externalSystemId, false)
  }

  override fun saveEntities(mainEntities: Collection<LibraryEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage,
                            writer: JpsFileContentWriter) = saveEntitiesTimeMs.addMeasuredTimeMillis {
    if (mainEntities.isEmpty()) return@addMeasuredTimeMillis

    val componentTag = JDomSerializationUtil.createComponentElement(LIBRARY_TABLE_COMPONENT_NAME)
    mainEntities.sortedBy { it.name }.forEach {
      val externalSystemId = (it.entitySource as? JpsImportedEntitySource)?.externalSystemId
      componentTag.addContent(saveLibrary(it, externalSystemId, isExternalStorage))
    }
    writer.saveComponent(fileUrl.url, LIBRARY_TABLE_COMPONENT_NAME, componentTag)
  }

  override fun toString(): String = "${javaClass.simpleName.substringAfterLast('.')}($fileUrl)"

  companion object {
    fun saveLibrary(library: LibraryEntity, externalSystemId: String?, isExternalStorage: Boolean): Element {
      val libraryTag = Element(LIBRARY_TAG)
      val legacyName = LibraryNameGenerator.getLegacyLibraryName(library.symbolicId)
      if (legacyName != null) {
        libraryTag.setAttribute(NAME_ATTRIBUTE, legacyName)
      }
      val customProperties = library.libraryProperties
      if (customProperties != null) {
        libraryTag.setAttribute(TYPE_ATTRIBUTE, customProperties.libraryType)
        val propertiesXmlTag = customProperties.propertiesXmlTag
        if (propertiesXmlTag != null) {
          libraryTag.addContent(JDOMUtil.load(propertiesXmlTag))
        }
      }
      if (externalSystemId != null) {
        val attributeName =
          if (isExternalStorage) SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE
          else SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE
        libraryTag.setAttribute(attributeName, externalSystemId)
      }
      val rootsMap = library.roots.groupByTo(HashMap()) { it.type }
      ROOT_TYPES_TO_WRITE_EMPTY_TAG.forEach {
        rootsMap.putIfAbsent(it, ArrayList())
      }
      val jarDirectoriesTags = ArrayList<Element>()
      rootsMap.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key.name }).forEach { (rootType, roots) ->
        val rootTypeTag = Element(rootType.name)
        roots.forEach {
          rootTypeTag.addContent(Element(ROOT_TAG).setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url.url))
        }
        roots.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.url.url }).forEach {
          if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
            val jarDirectoryTag = Element(JAR_DIRECTORY_TAG)
            jarDirectoryTag.setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url.url)
            jarDirectoryTag.setAttribute(RECURSIVE_ATTRIBUTE,
                                         (it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY).toString())
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
          excludedTag.addContent(Element(ROOT_TAG).setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, it.url.url))
        }
        libraryTag.addContent(excludedTag)
      }
      jarDirectoriesTags.forEach {
        libraryTag.addContent(it)
      }
      return libraryTag
    }

    fun loadLibrary(name: String, libraryElement: Element, libraryTableId: LibraryTableId, source: EntitySource,
                    virtualFileManager: VirtualFileUrlManager): LibraryEntity {
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
      val libProperties = type?.let {
        LibraryPropertiesEntity(type, source) {
          this.propertiesXmlTag = properties
        }
      }
      val excludes = excludedRoots.map { ExcludeUrlEntity(it, source) }
      val libraryEntity = LibraryEntity(name, libraryTableId, roots, source) {
        this.excludedRoots = excludes
        this.libraryProperties = libProperties
      }

      return libraryEntity
    }

    private val loadEntitiesTimeMs: AtomicLong = AtomicLong()
    private val saveEntitiesTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadEntitiesTimeCounter = meter.counterBuilder("jps.library.entities.serializer.load.entities.ms").buildObserver()
      val saveEntitiesTimeCounter = meter.counterBuilder("jps.library.entities.serializer.save.entities.ms").buildObserver()

      meter.batchCallback(
        {
          loadEntitiesTimeCounter.record(loadEntitiesTimeMs.get())
          saveEntitiesTimeCounter.record(saveEntitiesTimeMs.get())
        },
        loadEntitiesTimeCounter, saveEntitiesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
    }
  }
}

private const val DEFAULT_JAR_DIRECTORY_TYPE = "CLASSES"
private val libraryRootTypes = ConcurrentFactoryMap.createMap<String, LibraryRootTypeId> { LibraryRootTypeId(it) }
private val ROOT_TYPES_TO_WRITE_EMPTY_TAG = listOf("CLASSES", "SOURCES", "JAVADOC").map { libraryRootTypes[it]!! }

