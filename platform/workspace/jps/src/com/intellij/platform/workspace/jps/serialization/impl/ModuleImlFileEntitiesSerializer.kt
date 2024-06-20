// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader.isModulePropertiesBridgeEnabled
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import io.opentelemetry.api.metrics.Meter
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*
import org.jetbrains.jps.util.JpsPathUtil
import java.io.StringReader
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

internal const val DEPRECATED_MODULE_MANAGER_COMPONENT_NAME = "DeprecatedModuleOptionManager"
internal const val TEST_MODULE_PROPERTIES_COMPONENT_NAME = "TestModuleProperties"
private const val MODULE_ROOT_MANAGER_COMPONENT_NAME = "NewModuleRootManager"
private const val ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME = "AdditionalModuleElements"
internal const val URL_ATTRIBUTE = "url"
internal const val DUMB_ATTRIBUTE = "dumb"
private val STANDARD_MODULE_OPTIONS = setOf(
  "type", "external.system.id", "external.system.module.version", "external.linked.project.path", "external.linked.project.id",
  "external.root.project.path", "external.system.module.group", "external.system.module.type"
)
private val MODULE_OPTIONS_TO_CHECK = setOf(
  "external.system.module.version", "external.linked.project.path", "external.linked.project.id",
  "external.root.project.path", "external.system.module.group", "external.system.module.type"
)

internal open class ModuleImlFileEntitiesSerializer(internal val modulePath: ModulePath,
                                                    override val fileUrl: VirtualFileUrl,
                                                    override val internalEntitySource: JpsFileEntitySource,
                                                    protected val context: SerializationContext,
                                                    internal val internalModuleListSerializer: JpsModuleListSerializer? = null,
                                                    internal val externalModuleListSerializer: JpsModuleListSerializer? = null)
  : JpsFileEntitiesSerializer<ModuleEntity> {

  override val mainEntityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  protected open val skipLoadingIfFileDoesNotExist
    get() = false

  override fun equals(other: Any?) = other?.javaClass == javaClass && (other as ModuleImlFileEntitiesSerializer).modulePath == modulePath

  override fun hashCode() = modulePath.hashCode()

  override fun loadEntities(
    reader: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager
  ): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>> = loadEntitiesTimeMs.addMeasuredTime {

    val moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity> = HashMap()
    val newModuleEntity: ModuleEntity?
    val exceptionsCollector = ArrayList<Throwable>()
    if (!context.isExternalStorageEnabled) {
      // Loading data if the external storage is disabled
      val moduleLoadedInfo = loadModuleEntity(reader, errorReporter, virtualFileManager, moduleLibrariesCollector, exceptionsCollector)
      if (moduleLoadedInfo != null) {
        runCatchingXmlIssues(exceptionsCollector) {
          createFacetSerializer().loadFacetEntities(moduleLoadedInfo.moduleEntity, reader)
        }

        if (context.isOrphanageEnabled) {
          newModuleEntity = loadAdditionalContents(reader,
                                                   virtualFileManager,
                                                   moduleLoadedInfo.moduleEntity,
                                                   exceptionsCollector)
        }
        else {
          newModuleEntity = moduleLoadedInfo.moduleEntity
        }
      }
      else newModuleEntity = null
    }
    else {
      // Loading module from two files - external and local
      // Here we load BOTH external and internal iml file. This is done to load module once and attach all the information to it
      // It's a bit dirty, but we'll get rid of this logic when we'll remove external iml storage
      val externalSerializer = externalModuleListSerializer?.createSerializer(internalEntitySource, fileUrl,
                                                                              modulePath.group) as ModuleImlFileEntitiesSerializer?
      val moduleLoadedInfo = externalSerializer?.loadModuleEntity(reader, errorReporter, virtualFileManager, moduleLibrariesCollector,
                                                                  exceptionsCollector)
      val moduleEntity: ModuleEntity?
      if (moduleLoadedInfo != null) {
        val tmpModuleEntity = moduleLoadedInfo.moduleEntity
        val entitySource = getOtherEntitiesEntitySource(reader)
        runCatchingXmlIssues(exceptionsCollector) {
          loadContentRoots(moduleLoadedInfo.customRootsSerializer, moduleLoadedInfo.moduleEntity, reader,
                           moduleLoadedInfo.customDir, errorReporter, virtualFileManager, entitySource,
                           true, moduleLibrariesCollector)
        }

        if (context.isOrphanageEnabled) {
          moduleEntity = loadAdditionalContents(reader, virtualFileManager, moduleLoadedInfo.moduleEntity, exceptionsCollector)
        }
        else {
          moduleEntity = tmpModuleEntity
        }
      }
      else {
        val localModule = loadModuleEntity(reader, errorReporter, virtualFileManager, moduleLibrariesCollector, exceptionsCollector)

        var tmpModule = localModule?.moduleEntity

        if (context.isOrphanageEnabled && tmpModule != null) {
          tmpModule = loadAdditionalContents(reader, virtualFileManager, tmpModule, exceptionsCollector)
        }
        moduleEntity = tmpModule
      }
      if (moduleEntity != null) {
        runCatchingXmlIssues(exceptionsCollector) {
          createFacetSerializer().loadFacetEntities(moduleEntity, reader)
          externalSerializer?.createFacetSerializer()?.loadFacetEntities(moduleEntity, reader)
        }
        newModuleEntity = moduleEntity
      }
      else newModuleEntity = null
    }

    return@addMeasuredTime LoadingResult(
      mapOf(
        ModuleEntity::class.java to listOfNotNull(newModuleEntity),
        LibraryEntity::class.java to moduleLibrariesCollector.values,
      ),
      exceptionsCollector.firstOrNull(),
    )
  }

  private fun loadAdditionalContents(
    reader: JpsFileContentReader,
    virtualFileManager: VirtualFileUrlManager,
    moduleEntity: ModuleEntity.Builder,
    exceptionCollector: MutableList<Throwable>,
  ): ModuleEntity.Builder {
    val source = runCatchingXmlIssues(exceptionCollector) {
      getOtherEntitiesEntitySource(reader)
    } ?: return moduleEntity

    val roots = runCatchingXmlIssues(exceptionCollector) {
      loadAdditionalContentRoots(moduleEntity, reader, virtualFileManager, source)
    }

    if (roots == null) return moduleEntity
    return if (moduleEntity.isEmpty()) {
      ModuleEntity(moduleEntity.name, emptyList(), OrphanageWorkerEntitySource) {
        this.contentRoots = roots
      } as ModuleEntity.Builder
    }
    else {
      moduleEntity.contentRoots += roots
      moduleEntity
    }
  }

  override fun checkAndAddToBuilder(builder: MutableEntityStorage,
                                    orphanage: MutableEntityStorage,
                                    newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>) {

    val (orphans, elements) = newEntities.values.asSequence().flatten().partition { it.entitySource is OrphanageWorkerEntitySource }

    elements.forEach { builder addEntity it }
    orphans.forEach { orphanage addEntity it }
  }

  private class ModuleLoadedInfo(
    val moduleEntity: ModuleEntity.Builder,
    val customRootsSerializer: CustomModuleRootsSerializer?,
    val customDir: String?,
  )

  private fun loadModuleEntity(reader: JpsFileContentReader,
                               errorReporter: ErrorReporter,
                               virtualFileManager: VirtualFileUrlManager,
                               moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>,
                               exceptionsCollector: MutableList<Throwable>): ModuleLoadedInfo? {
    if (skipLoadingIfFileDoesNotExist && !fileUrl.toPath().exists()) {
      return null
    }

    val moduleOptions: Map<String?, String?>
    val customRootsSerializer: CustomModuleRootsSerializer?
    val customDir: String?
    val externalSystemOptions: Map<String?, String?>
    val externalSystemId: String?
    val entitySourceForModuleAndOtherEntities = runCatchingXmlIssues {
      moduleOptions = readModuleOptions(reader)
      val pair = readExternalSystemOptions(reader, moduleOptions)
      externalSystemOptions = pair.first
      externalSystemId = pair.second

      customRootsSerializer = moduleOptions[JpsProjectLoader.CLASSPATH_ATTRIBUTE]?.let { customSerializerId ->
        val serializer = context.customModuleRootsSerializers.firstOrNull { it.id == customSerializerId }
        if (serializer == null) {
          errorReporter.reportError(
            WorkspaceModelJpsBundle.message("error.message.unknown.classpath.provider", fileUrl.fileName, customSerializerId), fileUrl)
        }
        return@let serializer
      }

      customDir = moduleOptions[JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE]
      val externalSystemEntitySource = createEntitySource(externalSystemId)
      val moduleEntitySource = customRootsSerializer?.createEntitySource(fileUrl, internalEntitySource, customDir, virtualFileManager)
                               ?: externalSystemEntitySource
      if (moduleEntitySource is DummyParentEntitySource) {
        Pair(moduleEntitySource, externalSystemEntitySource)
      }
      else {
        Pair(moduleEntitySource, moduleEntitySource)
      }
    }
      .onFailure {
        exceptionsCollector.add(it)
        val module = ModuleEntity(modulePath.moduleName, listOf(ModuleSourceDependency),
                                  internalEntitySource) as ModuleEntity.Builder
        return ModuleLoadedInfo(module, null, null)
      }
      .getOrThrow()

    val moduleEntity = ModuleEntity(modulePath.moduleName, listOf(ModuleSourceDependency),
                                    entitySourceForModuleAndOtherEntities.first) as ModuleEntity.Builder

    val entitySource = entitySourceForModuleAndOtherEntities.second
    val moduleGroup = modulePath.group
    if (moduleGroup != null) {
      ModuleGroupPathEntity(moduleGroup.split('/'), entitySource) {
        this.module = moduleEntity
      }
    }

    val moduleType = moduleOptions["type"]
    if (moduleType != null) {
      moduleEntity.type = moduleType
    }
    @Suppress("UNCHECKED_CAST")
    val customModuleOptions =
      moduleOptions.filter { (key, value) -> key != null && value != null && key !in STANDARD_MODULE_OPTIONS } as Map<String, String>
    if (customModuleOptions.isNotEmpty()) {
      ModuleCustomImlDataEntity(customModuleOptions, entitySource) {
        this.rootManagerTagCustomData = null
        this.module = moduleEntity
      }
    }

    context.customModuleComponentSerializers.forEach {
      it.loadComponent(moduleEntity, reader, fileUrl, errorReporter, virtualFileManager)
    }

    runCatchingXmlIssues(exceptionsCollector) {
      // Don't forget to load external system options even if custom root serializer exist
      loadExternalSystemOptions(moduleEntity, reader, externalSystemOptions, externalSystemId, entitySource)

      loadContentRoots(customRootsSerializer, moduleEntity, reader, customDir, errorReporter, virtualFileManager, moduleEntity.entitySource,
                       false, moduleLibrariesCollector)
      loadTestModuleProperty(moduleEntity, reader, entitySource)
    }

    return ModuleLoadedInfo(moduleEntity, customRootsSerializer, customDir)
  }

  /**
   * [loadingAdditionalRoots] - true if we load additional information of the module. For example, content roots that are defined by user
   *   in maven project.
   */
  private fun loadContentRoots(customRootsSerializer: CustomModuleRootsSerializer?,
                               moduleEntity: ModuleEntity.Builder,
                               reader: JpsFileContentReader,
                               customDir: String?,
                               errorReporter: ErrorReporter,
                               virtualFileManager: VirtualFileUrlManager,
                               contentRootEntitySource: EntitySource,
                               loadingAdditionalRoots: Boolean,
                               moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>) {
    if (customRootsSerializer != null) {
      customRootsSerializer.loadRoots(moduleEntity, reader, customDir, fileUrl, internalModuleListSerializer, errorReporter,
                                      virtualFileManager,
                                      moduleLibrariesCollector)
    }
    else {
      val rootManagerElement = reader.loadComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, getBaseDirPath())?.clone()
      if (rootManagerElement != null) {
        loadRootManager(rootManagerElement, moduleEntity, virtualFileManager, contentRootEntitySource, loadingAdditionalRoots,
                        moduleLibrariesCollector)
      }
    }
  }

  private fun loadAdditionalContentRoots(
    moduleEntity: ModuleEntity.Builder,
    reader: JpsFileContentReader,
    virtualFileManager: VirtualFileUrlManager,
    contentRootEntitySource: EntitySource,
  ): List<ContentRootEntity>? {
    val additionalElements = reader.loadComponent(fileUrl.url, ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME, getBaseDirPath())?.clone()
                             ?: return null
    return loadContentRootEntities(moduleEntity, additionalElements, virtualFileManager, contentRootEntitySource)
  }

  private fun getOtherEntitiesEntitySource(reader: JpsFileContentReader): EntitySource {
    val moduleOptions = readModuleOptions(reader)
    val pair = readExternalSystemOptions(reader, moduleOptions)
    return createEntitySource(pair.second)
  }

  protected open fun getBaseDirPath(): String? = null

  protected open fun readExternalSystemOptions(reader: JpsFileContentReader,
                                               moduleOptions: Map<String?, String?>): Pair<Map<String?, String?>, String?> {
    val externalSystemId = moduleOptions["external.system.id"]
                           ?: if (moduleOptions[SerializationConstants.IS_MAVEN_MODULE_IML_ATTRIBUTE] == true.toString()) SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
                           else null
    return Pair(moduleOptions, externalSystemId)
  }

  private fun readModuleOptions(reader: JpsFileContentReader): Map<String?, String?> {
    val component = reader.loadComponent(fileUrl.url, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, getBaseDirPath()) ?: return emptyMap()
    return component.getChildren("option").associateBy({ it.getAttributeValue("key") },
                                                       { it.getAttributeValue("value") })
  }

  protected open fun loadExternalSystemOptions(module: ModuleEntity,
                                               reader: JpsFileContentReader,
                                               externalSystemOptions: Map<String?, String?>,
                                               externalSystemId: String?,
                                               entitySource: EntitySource) {
    if (!shouldCreateExternalSystemModuleOptions(externalSystemId, externalSystemOptions, MODULE_OPTIONS_TO_CHECK)) return
    ExternalSystemModuleOptionsEntity(entitySource) {
      this.module = module
      externalSystem = externalSystemId
      externalSystemModuleVersion = externalSystemOptions["external.system.module.version"]
      linkedProjectPath = externalSystemOptions["external.linked.project.path"]
      linkedProjectId = externalSystemOptions["external.linked.project.id"]
      rootProjectPath = externalSystemOptions["external.root.project.path"]
      externalSystemModuleGroup = externalSystemOptions["external.system.module.group"]
      externalSystemModuleType = externalSystemOptions["external.system.module.type"]
    }
  }

  internal fun shouldCreateExternalSystemModuleOptions(externalSystemId: String?,
                                                       externalSystemOptions: Map<String?, String?>,
                                                       moduleOptionsToCheck: Set<String>): Boolean {
    if (externalSystemId != null) return true
    return externalSystemOptions.any { (key, value) -> value != null && key in moduleOptionsToCheck }
  }

  private fun loadRootManager(rootManagerElement: Element,
                              moduleEntity: ModuleEntity.Builder,
                              virtualFileManager: VirtualFileUrlManager,
                              contentRootEntitySource: EntitySource,
                              loadingAdditionalRoots: Boolean,
                              moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>) {
    val contentRoots = loadContentRootEntities(moduleEntity, rootManagerElement, virtualFileManager, contentRootEntitySource)
    moduleEntity.contentRoots += contentRoots

    val dependencyItems = loadModuleDependencies(rootManagerElement, contentRootEntitySource, virtualFileManager, moduleEntity.symbolicId,
                                                 moduleLibrariesCollector)
    if (!loadingAdditionalRoots) {
      moduleEntity.dependencies = dependencyItems.mapNotNullTo(mutableListOf()) { it.getOrNull() }
    }

    if (!loadingAdditionalRoots) {
      val javaModuleSettings = JavaSettingsSerializer.loadJavaModuleSettings(rootManagerElement, context, contentRootEntitySource)
      if (javaModuleSettings != null) {
        moduleEntity.javaSettings = javaModuleSettings
      }
    }
    if (!JDOMUtil.isEmpty(rootManagerElement)) {
      val customImlData = moduleEntity.customImlData
      if (customImlData == null) {
        val imlData = ModuleCustomImlDataEntity(emptyMap(), contentRootEntitySource) {
          this.rootManagerTagCustomData = JDOMUtil.write(rootManagerElement)
        }
        moduleEntity.customImlData = imlData
      }
      else {
        (customImlData as ModuleCustomImlDataEntity.Builder).rootManagerTagCustomData = JDOMUtil.write(rootManagerElement)
      }
    }

    // Load other elements and throw exception
    // Maybe it makes sense to pass this exception to the code above.
    dependencyItems.firstOrNull { it.isFailure }?.let { throw it.exceptionOrNull()!! }
  }

  private fun loadModuleDependencies(rootManagerElement: Element,
                                     contentRootEntitySource: EntitySource,
                                     virtualFileManager: VirtualFileUrlManager,
                                     moduleId: ModuleId,
                                     moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>): List<Result<ModuleDependencyItem>> {
    fun Element.readScope(): DependencyScope {
      val attributeValue = getAttributeValue(SCOPE_ATTRIBUTE) ?: return DependencyScope.COMPILE
      return try {
        DependencyScope.valueOf(attributeValue)
      }
      catch (e: IllegalArgumentException) {
        DependencyScope.COMPILE
      }
    }

    fun Element.isExported() = getAttributeValue(EXPORTED_ATTRIBUTE) != null
    val moduleLibraryNames = mutableSetOf<String>()
    var nextUnnamedLibraryIndex = 1
    val dependencyItems = runCatchingXmlIssues { rootManagerElement.getChildrenAndDetach(ORDER_ENTRY_TAG) }
      .onFailure { return listOf(Result.success(ModuleSourceDependency)) }
      .getOrThrow()
      .mapTo(ArrayList()) { dependencyElement ->
        runCatchingXmlIssues {
          when (val orderEntryType = dependencyElement.getAttributeValue(TYPE_ATTRIBUTE)) {
            SOURCE_FOLDER_TYPE -> ModuleSourceDependency
            JDK_TYPE -> SdkDependency(SdkId(dependencyElement.getAttributeValueStrict(JDK_NAME_ATTRIBUTE),
                                                           dependencyElement.getAttributeValue(JDK_TYPE_ATTRIBUTE)))
            INHERITED_JDK_TYPE -> InheritedSdkDependency
            LIBRARY_TYPE -> {
              val level = dependencyElement.getAttributeValueStrict(LEVEL_ATTRIBUTE)
              val parentId = LibraryNameGenerator.getLibraryTableId(level)
              val libraryId = LibraryId(dependencyElement.getAttributeValueStrict(NAME_ATTRIBUTE), parentId)
              LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
            }
            MODULE_LIBRARY_TYPE -> {
              val libraryElement = dependencyElement.getChildTagStrict(LIBRARY_TAG)
              // TODO. Probably we want a fixed name based on hashed library roots
              val nameAttributeValue = libraryElement.getAttributeValue(NAME_ATTRIBUTE)
              val originalName = nameAttributeValue ?: "${LibraryNameGenerator.UNNAMED_LIBRARY_NAME_PREFIX}${nextUnnamedLibraryIndex++}"
              val name = LibraryNameGenerator.generateUniqueLibraryName(originalName) { it in moduleLibraryNames }
              moduleLibraryNames.add(name)
              val tableId = LibraryTableId.ModuleLibraryTableId(moduleId)
              val library = JpsLibraryEntitiesSerializer.loadLibrary(name, libraryElement, tableId, contentRootEntitySource, virtualFileManager)
              val libraryId = LibraryId(name, tableId)
              moduleLibrariesCollector[libraryId] = library
              LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
            }
            MODULE_TYPE -> {
              val depModuleName = dependencyElement.getAttributeValueStrict(MODULE_NAME_ATTRIBUTE)
              ModuleDependency(ModuleId(depModuleName), dependencyElement.isExported(),
                                                               dependencyElement.readScope(),
                                                               dependencyElement.getAttributeValue("production-on-test") != null)
            }
            else -> throw JDOMException("Unexpected '$TYPE_ATTRIBUTE' attribute in '$ORDER_ENTRY_TAG' tag: $orderEntryType")
          }
        }
      }

    if (dependencyItems.none { it.getOrNull() is ModuleSourceDependency }) {
      dependencyItems.add(Result.success(ModuleSourceDependency))
    }

    return dependencyItems
  }

  private fun loadContentRootEntities(moduleEntity: ModuleEntity,
                                      rootManagerElement: Element,
                                      virtualFileManager: VirtualFileUrlManager,
                                      contentRootEntitySource: EntitySource): List<ContentRootEntity> {
    val alreadyLoadedContentRoots = moduleEntity.contentRoots.associateBy { it.url.url }
    return rootManagerElement.getChildrenAndDetach(CONTENT_TAG).mapNotNull { contentElement ->

      // Load SOURCE ROOTS
      val sourceRoots = loadSourceRoots(contentElement, virtualFileManager, contentRootEntitySource)
      val sourceRootOrder = createSourceRootsOrder(sourceRoots.map { it.url }, contentRootEntitySource)


      // Load EXCLUDES
      val excludes = loadContentRootExcludes(contentElement, virtualFileManager, contentRootEntitySource)


      val contentRootUrlString = contentElement.getAttributeValueStrict(URL_ATTRIBUTE)
      val isDumb = contentElement.getAttributeValue(DUMB_ATTRIBUTE).toBoolean()
      val contentRoot = alreadyLoadedContentRoots[contentRootUrlString]
      if (contentRoot == null) {
        val contentRootUrl = contentRootUrlString.let { virtualFileManager.getOrCreateFromUri(it) }
        val excludePatterns = contentElement.getChildren(EXCLUDE_PATTERN_TAG).map { it.getAttributeValue(EXCLUDE_PATTERN_ATTRIBUTE) }
        val source = if (isDumb) OrphanageWorkerEntitySource else contentRootEntitySource
        ContentRootEntity(contentRootUrl, excludePatterns, source) {
          this.sourceRoots = sourceRoots
          this.sourceRootOrder = sourceRootOrder
          this.excludedUrls = excludes
          this.excludeUrlOrder = if (excludes.size > 1) ExcludeUrlOrderEntity(excludes.map { it.url }, contentRootEntitySource) else null
        }
      }
      else {

        (contentRoot as ContentRootEntity.Builder).apply {
          this.sourceRoots += sourceRoots
          this.excludedUrls += excludes
        }
        // Add order of source roots
        if (sourceRootOrder != null) {
          val existingOrder = contentRoot.sourceRootOrder
          if (existingOrder != null) {
            (existingOrder as SourceRootOrderEntity.Builder).orderOfSourceRoots.addAll(sourceRootOrder.orderOfSourceRoots)
          }
          else {
            contentRoot.sourceRootOrder = sourceRootOrder
          }
        }
        // Add order of excludes
        if (excludes.size > 1) {
          val existingExcludesOrder = contentRoot.excludeUrlOrder
          if (existingExcludesOrder != null) {
            (existingExcludesOrder as ExcludeUrlOrderEntity.Builder).order.addAll(excludes.map { it.url })
          }
          else {
            contentRoot.excludeUrlOrder = ExcludeUrlOrderEntity(excludes.map { it.url }, contentRootEntitySource)
          }
        }

        null
      }
    }
  }

  private fun loadContentRootExcludes(
    contentElement: Element,
    virtualFileManager: VirtualFileUrlManager,
    entitySource: EntitySource,
  ): List<ExcludeUrlEntity> {
    return contentElement
      .getChildren(EXCLUDE_FOLDER_TAG)
      .map { virtualFileManager.getOrCreateFromUri(it.getAttributeValueStrict(URL_ATTRIBUTE)) }
      .map { exclude ->
        ExcludeUrlEntity(exclude, entitySource)
      }
  }

  private fun loadSourceRoots(
    contentElement: Element,
    virtualFileManager: VirtualFileUrlManager,
    sourceRootSource: EntitySource,
  ): List<SourceRootEntity> {

    return contentElement.getChildren(SOURCE_FOLDER_TAG).map { sourceRootElement ->
      val isTestSource = sourceRootElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)?.toBoolean() == true
      val type = sourceRootElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE)
                 ?: (if (isTestSource) JAVA_TEST_ROOT_TYPE_ID else JAVA_SOURCE_ROOT_TYPE_ID)

      val sourceRoot = SourceRootEntity(
        url = virtualFileManager.getOrCreateFromUri(sourceRootElement.getAttributeValueStrict(URL_ATTRIBUTE)),
        rootType = type,
        entitySource = sourceRootSource
      )

      // Attach different attribute entities to the source root
      if (type == JAVA_SOURCE_ROOT_TYPE_ID || type == JAVA_TEST_ROOT_TYPE_ID) {
        val generated = sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false
        val packagePrefix = sourceRootElement.getAttributeValue(PACKAGE_PREFIX_ATTRIBUTE) ?: ""
        JavaSourceRootPropertiesEntity(generated, packagePrefix, sourceRootSource) {
          this.sourceRoot = sourceRoot
        }
      }
      else if (type == JAVA_RESOURCE_ROOT_ID || type == JAVA_TEST_RESOURCE_ROOT_ID) {
        val generated = sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false
        val relativeOutputPath = sourceRootElement.getAttributeValue(RELATIVE_OUTPUT_PATH_ATTRIBUTE) ?: ""
        JavaResourceRootPropertiesEntity(generated, relativeOutputPath, sourceRootSource) {
          this.sourceRoot = sourceRoot
        }
      }
      else {
        val elem = sourceRootElement.clone()
        elem.removeAttribute(URL_ATTRIBUTE)
        elem.removeAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE)
        if (!JDOMUtil.isEmpty(elem)) {
          CustomSourceRootPropertiesEntity(JDOMUtil.write(elem), sourceRoot.entitySource) {
            this.sourceRoot = sourceRoot
          }
        }
      }

      sourceRoot
    }
  }

  private fun loadTestModuleProperty(moduleEntity: ModuleEntity.Builder, reader: JpsFileContentReader, entitySource: EntitySource) {
    if (!isModulePropertiesBridgeEnabled) return
    val component = reader.loadComponent(fileUrl.url, TEST_MODULE_PROPERTIES_COMPONENT_NAME) ?: return
    val productionModuleName = component.getAttribute(PRODUCTION_MODULE_NAME_ATTRIBUTE).value
    if (productionModuleName.isEmpty()) return
    moduleEntity.testProperties = TestModulePropertiesEntity(ModuleId(productionModuleName), entitySource)
  }

  private fun Element.getChildrenAndDetach(cname: String): List<Element> {
    val result = getChildren(cname).toList()
    result.forEach { it.detach() }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  override fun saveEntities(mainEntities: Collection<ModuleEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage,
                            writer: JpsFileContentWriter) = saveEntitiesTimeMs.addMeasuredTime {

    val module = mainEntities.singleOrNull()
    if (module != null && acceptsSource(module.entitySource)) {
      saveModuleEntities(module, entities, storage, writer)
    }
    else {
      val targetComponent = if (context.isOrphanageEnabled) ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME else MODULE_ROOT_MANAGER_COMPONENT_NAME
      if (ContentRootEntity::class.java in entities || SourceRootEntity::class.java in entities || ExcludeUrlEntity::class.java in entities) {
        val contentEntities = entities[ContentRootEntity::class.java] as? List<ContentRootEntity> ?: emptyList()
        val sourceRootEntities = (entities[SourceRootEntity::class.java] as? List<SourceRootEntity>)?.toMutableSet() ?: mutableSetOf()
        val excludeRoots = (entities[ExcludeUrlEntity::class.java] as? List<ExcludeUrlEntity>)?.filter { it.contentRoot != null }?.toMutableSet()
                           ?: mutableSetOf()
        val rootElement = JDomSerializationUtil.createComponentElement(targetComponent)
        if (contentEntities.isNotEmpty()) {
          contentEntities.forEach {
            it.sourceRoots.filter { sourceRootEntity -> acceptsSource(sourceRootEntity.entitySource) }.forEach { sourceRootEntity ->
              sourceRootEntities.remove(sourceRootEntity)
            }
            it.excludedUrls.filter { exclude -> acceptsSource(exclude.entitySource) }.forEach { exclude ->
              excludeRoots.remove(exclude)
            }
          }
          saveContentEntities(contentEntities, rootElement)
          writer.saveComponent(fileUrl.url, targetComponent, rootElement)
        }
        if (sourceRootEntities.isNotEmpty() || excludeRoots.isNotEmpty()) {
          val excludes = excludeRoots.groupBy { it.contentRoot!!.url }.toMutableMap()
          if (sourceRootEntities.isNotEmpty()) {
            sourceRootEntities.groupBy { it.contentRoot }
              .toSortedMap(compareBy { it.url.url })
              .forEach { (contentRoot, sourceRoots) ->
                val contentRootTag = Element(CONTENT_TAG)
                contentRootTag.setAttribute(URL_ATTRIBUTE, contentRoot.url.url)
                if (context.isOrphanageEnabled) {
                  contentRootTag.setAttribute(DUMB_ATTRIBUTE, true.toString())
                }
                saveSourceRootEntities(sourceRoots, contentRootTag, contentRoot.getSourceRootsComparator())
                excludes[contentRoot.url]?.let {
                  saveExcludeUrls(contentRootTag, it)
                  excludes.remove(contentRoot.url)
                }
                rootElement.addContent(contentRootTag)
                writer.saveComponent(fileUrl.url, targetComponent, rootElement)
              }
          }
          excludes.toSortedMap(compareBy { it.url }).forEach { (url, exclude) ->
            val contentRootTag = Element(CONTENT_TAG)
            contentRootTag.setAttribute(URL_ATTRIBUTE, url.url)
            if (context.isOrphanageEnabled) {
              contentRootTag.setAttribute(DUMB_ATTRIBUTE, true.toString())
            }
            saveExcludeUrls(contentRootTag, exclude)
            rootElement.addContent(contentRootTag)
            writer.saveComponent(fileUrl.url, targetComponent, rootElement)
          }
        }

        if (context.isOrphanageEnabled) {
          // Component to save additional roots before introducing AdditionalModuleElements.
          // It's not used for this function anymore and should be cleared
          writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
        }
      }
      else {
        writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
        writer.saveComponent(fileUrl.url, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
        if (context.isOrphanageEnabled) {
          writer.saveComponent(fileUrl.url, ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME, null)
        }
      }
    }

    createFacetSerializer().saveFacetEntities(module, entities, writer, this::acceptsSource)
  }

  protected open fun createFacetSerializer(): FacetsSerializer {
    return FacetsSerializer(fileUrl, internalEntitySource, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, null, false, context)
  }

  protected open fun acceptsSource(entitySource: EntitySource): Boolean {
    return entitySource is JpsFileEntitySource ||
           entitySource is CustomModuleEntitySource ||
           entitySource is JpsFileDependentEntitySource && (entitySource as? JpsImportedEntitySource)?.storedExternally != true
  }

  private fun saveModuleEntities(module: ModuleEntity,
                                 entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                 storage: EntityStorage,
                                 writer: JpsFileContentWriter) {
    val externalSystemOptions = module.exModuleOptions
    val customImlData = module.customImlData
    saveModuleOptions(externalSystemOptions, module.type, customImlData, writer)
    val moduleOptions = customImlData?.customModuleOptions
    val customSerializerId = moduleOptions?.get(JpsProjectLoader.CLASSPATH_ATTRIBUTE)
    if (customSerializerId != null) {
      val serializer = context.customModuleRootsSerializers.firstOrNull { it.id == customSerializerId }
      if (serializer != null) {
        val customDir = moduleOptions[JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE]
        serializer.saveRoots(module, entities, writer, customDir, fileUrl, storage, context.virtualFileUrlManager)
        writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
      }
      else {
        LOG.warn("Classpath storage provider $customSerializerId not found")
      }
    }
    else {
      saveRootManagerElement(module, customImlData, entities, writer)
    }
    for (it in context.customModuleComponentSerializers) {
      it.saveComponent(module, fileUrl, writer)
    }
    saveTestModuleProperty(module, writer)
  }

  private fun saveRootManagerElement(module: ModuleEntity,
                                     customImlData: ModuleCustomImlDataEntity?,
                                     entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                     writer: JpsFileContentWriter) {
    val rootManagerElement = JDomSerializationUtil.createComponentElement(MODULE_ROOT_MANAGER_COMPONENT_NAME)
    JavaSettingsSerializer.saveJavaSettings(module.javaSettings, rootManagerElement, context)

    if (customImlData != null) {
      val rootManagerTagCustomData = customImlData.rootManagerTagCustomData
      if (rootManagerTagCustomData != null) {
        val element = JDOMUtil.load(StringReader(rootManagerTagCustomData))
        JDOMUtil.merge(rootManagerElement, element)
      }
    }
    rootManagerElement.attributes.sortWith(knownAttributesComparator)
    //todo ensure that custom data is written in proper order

    val contentEntities = module.contentRoots.filter { acceptsSource(it.entitySource) }

    saveContentEntities(contentEntities, rootManagerElement)

    @Suppress("UNCHECKED_CAST")
    val moduleLibraries = (entities[LibraryEntity::class.java] as List<LibraryEntity>? ?: emptyList()).associateBy { it.name }
    module.dependencies.forEach {
      val tag = saveDependencyItem(it, moduleLibraries)
      if (tag != null) {
        rootManagerElement.addContent(tag)
      }
    }

    writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootManagerElement)
  }

  private fun saveContentEntities(contentEntities: List<ContentRootEntity>,
                                  rootManagerElement: Element) {
    contentEntities.sortedBy { it.url.url }.forEach { contentEntry ->
      val contentRootTag = Element(CONTENT_TAG)
      contentRootTag.setAttribute(URL_ATTRIBUTE, contentEntry.url.url)

      saveSourceRootEntities(contentEntry.sourceRoots.filter { acceptsSource(it.entitySource) }, contentRootTag,
                             contentEntry.getSourceRootsComparator())

      saveExcludeUrls(contentRootTag, contentEntry.excludedUrls.filter { acceptsSource(it.entitySource) })
      contentEntry.excludedPatterns.forEach {
        contentRootTag.addContent(Element(EXCLUDE_PATTERN_TAG).setAttribute(EXCLUDE_PATTERN_ATTRIBUTE, it))
      }
      rootManagerElement.addContent(contentRootTag)
    }
  }

  private fun saveExcludeUrls(contentRootTag: Element, excludeUrls: List<ExcludeUrlEntity>) {
    val urlMap = excludeUrls.groupByTo(HashMap()) { it.url }
    val contentEntry = excludeUrls.firstOrNull()?.contentRoot ?: return
    val newOrder = sortByOrderEntity(contentEntry.excludeUrlOrder?.order, urlMap) {
      this.sortedBy { it.url.url }
    }
    newOrder.forEach {
      contentRootTag.addContent(Element(EXCLUDE_FOLDER_TAG).setAttribute(URL_ATTRIBUTE, it.url.url))
    }
  }

  private fun saveSourceRootEntities(sourceRoots: Collection<SourceRootEntity>,
                                     contentRootTag: Element,
                                     sourceRootEntityComparator: Comparator<SourceRootEntity>) {
    var mySourceRoots = sourceRoots
    mySourceRoots = mySourceRoots.sortedWith(sourceRootEntityComparator)
    mySourceRoots.forEach {
      contentRootTag.addContent(saveSourceRoot(it))
    }
  }

  private fun createEntitySource(externalSystemId: String?): EntitySource {
    if (externalSystemId == null) return internalEntitySource
    return createExternalEntitySource(externalSystemId)
  }

  protected open fun createExternalEntitySource(externalSystemId: String): EntitySource = JpsImportedEntitySource(internalEntitySource,
                                                                                                                  externalSystemId, false)

  private fun saveDependencyItem(dependencyItem: ModuleDependencyItem,
                                 moduleLibraries: Map<String, LibraryEntity>) = when (dependencyItem) {
    is ModuleSourceDependency -> createOrderEntryTag(SOURCE_FOLDER_TYPE).setAttribute("forTests", "false")
    is SdkDependency -> createOrderEntryTag(JDK_TYPE).apply {
      setAttribute(JDK_NAME_ATTRIBUTE, dependencyItem.sdk.name)

      val sdkType = dependencyItem.sdk.type
      setAttribute(JDK_TYPE_ATTRIBUTE, sdkType)
    }
    is InheritedSdkDependency -> createOrderEntryTag(INHERITED_JDK_TYPE)
    is LibraryDependency -> {
      val library = dependencyItem.library
      if (library.tableId is LibraryTableId.ModuleLibraryTableId) {
        val moduleLibrary = moduleLibraries[library.name]
        if (moduleLibrary != null) {
          createOrderEntryTag(MODULE_LIBRARY_TYPE).apply {
            setExportedAndScopeAttributes(dependencyItem)
            addContent(JpsLibraryEntitiesSerializer.saveLibrary(moduleLibrary, null, false))
          }
        }
        else {
          LOG.error("""
            |Module-level library '${library.name}' from module '${library.tableId.moduleId}' cannot be saved by $this, because its entity
            |was not among ${moduleLibraries.size} library entities passed to serializer:
            |${moduleLibraries.keys.joinToString("\n")}
            |""".trimMargin())
          null
        }
      }
      else {
        createOrderEntryTag(LIBRARY_TYPE).apply {
          setExportedAndScopeAttributes(dependencyItem)
          setAttribute(NAME_ATTRIBUTE, library.name)
          setAttribute(LEVEL_ATTRIBUTE, library.tableId.level)
        }
      }
    }
    is ModuleDependency -> createOrderEntryTag(MODULE_TYPE).apply {
      setAttribute(MODULE_NAME_ATTRIBUTE, dependencyItem.module.name)
      setExportedAndScopeAttributes(dependencyItem)
      if (dependencyItem.productionOnTest) {
        setAttribute("production-on-test", "")
      }
    }
  }

  protected open fun saveModuleOptions(externalSystemOptions: ExternalSystemModuleOptionsEntity?,
                                       moduleType: String?,
                                       customImlData: ModuleCustomImlDataEntity?,
                                       writer: JpsFileContentWriter) {
    val optionsMap = TreeMap<String, String?>()
    if (externalSystemOptions != null) {
      if (externalSystemOptions.externalSystem == SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID) {
        optionsMap[SerializationConstants.IS_MAVEN_MODULE_IML_ATTRIBUTE] = true.toString()
      }
      else {
        optionsMap["external.system.id"] = externalSystemOptions.externalSystem
      }
      optionsMap["external.root.project.path"] = externalSystemOptions.rootProjectPath
      optionsMap["external.linked.project.id"] = externalSystemOptions.linkedProjectId
      optionsMap["external.linked.project.path"] = externalSystemOptions.linkedProjectPath
      optionsMap["external.system.module.type"] = externalSystemOptions.externalSystemModuleType
      optionsMap["external.system.module.group"] = externalSystemOptions.externalSystemModuleGroup
      optionsMap["external.system.module.version"] = externalSystemOptions.externalSystemModuleVersion
    }
    optionsMap["type"] = moduleType
    if (customImlData != null) {
      optionsMap.putAll(customImlData.customModuleOptions)
    }
    val componentTag = JDomSerializationUtil.createComponentElement(DEPRECATED_MODULE_MANAGER_COMPONENT_NAME)
    for ((name, value) in optionsMap) {
      if (value != null) {
        componentTag.addContent(Element("option").setAttribute("key", name).setAttribute("value", value))
      }
    }
    if (componentTag.children.isNotEmpty()) {
      writer.saveComponent(fileUrl.url, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, componentTag)
    }
  }

  private fun Element.setExportedAndScopeAttributes(item: ModuleDependency) {
    if (item.exported) {
      setAttribute(EXPORTED_ATTRIBUTE, "")
    }
    if (item.scope != DependencyScope.COMPILE) {
      setAttribute(SCOPE_ATTRIBUTE, item.scope.name)
    }
  }

  private fun Element.setExportedAndScopeAttributes(item: LibraryDependency) {
    if (item.exported) {
      setAttribute(EXPORTED_ATTRIBUTE, "")
    }
    if (item.scope != DependencyScope.COMPILE) {
      setAttribute(SCOPE_ATTRIBUTE, item.scope.name)
    }
  }

  private fun createOrderEntryTag(type: String) = Element(ORDER_ENTRY_TAG).setAttribute(TYPE_ATTRIBUTE, type)

  private fun saveSourceRoot(sourceRoot: SourceRootEntity): Element {
    val sourceRootTag = Element(SOURCE_FOLDER_TAG)
    sourceRootTag.setAttribute(URL_ATTRIBUTE, sourceRoot.url.url)
    val rootType = sourceRoot.rootType
    if (rootType !in listOf(JAVA_SOURCE_ROOT_TYPE_ID, JAVA_TEST_ROOT_TYPE_ID)) {
      sourceRootTag.setAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE, rootType)
    }
    val javaRootProperties = sourceRoot.asJavaSourceRoot()
    if (javaRootProperties != null) {
      sourceRootTag.setAttribute(IS_TEST_SOURCE_ATTRIBUTE, (rootType == JAVA_TEST_ROOT_TYPE_ID).toString())
      val packagePrefix = javaRootProperties.packagePrefix
      if (packagePrefix.isNotEmpty()) {
        sourceRootTag.setAttribute(PACKAGE_PREFIX_ATTRIBUTE, packagePrefix)
      }
      if (javaRootProperties.generated) {
        sourceRootTag.setAttribute(IS_GENERATED_ATTRIBUTE, true.toString())
      }
    }

    val javaResourceRootProperties = sourceRoot.asJavaResourceRoot()
    if (javaResourceRootProperties != null) {
      val relativeOutputPath = javaResourceRootProperties.relativeOutputPath
      if (relativeOutputPath.isNotEmpty()) {
        sourceRootTag.setAttribute(RELATIVE_OUTPUT_PATH_ATTRIBUTE, relativeOutputPath)
      }
      if (javaResourceRootProperties.generated) {
        sourceRootTag.setAttribute(IS_GENERATED_ATTRIBUTE, true.toString())
      }
    }
    val customProperties = sourceRoot.customSourceRootProperties
    if (customProperties != null) {
      val element = JDOMUtil.load(StringReader(customProperties.propertiesXmlTag))
      JDOMUtil.merge(sourceRootTag, element)
    }
    return sourceRootTag
  }

  private fun saveTestModuleProperty(moduleEntity: ModuleEntity, writer: JpsFileContentWriter) {
    if (!isModulePropertiesBridgeEnabled) return
    val testProperties = moduleEntity.testProperties ?: return
    val testModulePropertyTag = Element(TEST_MODULE_PROPERTIES_COMPONENT_NAME)
    testModulePropertyTag.setAttribute(PRODUCTION_MODULE_NAME_ATTRIBUTE, testProperties.productionModuleId.presentableName)
    writer.saveComponent(fileUrl.url, TEST_MODULE_PROPERTIES_COMPONENT_NAME, testModulePropertyTag)
  }

  override val additionalEntityTypes: List<Class<out WorkspaceEntity>>
    get() = listOf(SourceRootOrderEntity::class.java)

  override fun toString(): String = "ModuleImlFileEntitiesSerializer($fileUrl)"

  companion object {
    private val LOG = logger<ModuleImlFileEntitiesSerializer>()

    // The comparator has reversed priority. So, the last entry of this list will be printed as a first attribute in the xml tag.
    private val orderOfKnownAttributes = listOf(
      INHERIT_COMPILER_OUTPUT_ATTRIBUTE,
      MODULE_LANGUAGE_LEVEL_ATTRIBUTE,
      URL_ATTRIBUTE,
      "name"
    )

    // Reversed comparator for attributes. Unknown attributes will be pushed to the end (since they return -1 in the [indexOf]).
    private val knownAttributesComparator = Comparator<Attribute> { o1, o2 ->
      orderOfKnownAttributes.indexOf(o1.name).compareTo(orderOfKnownAttributes.indexOf(o2.name))
    }.reversed()

    private val loadEntitiesTimeMs = MillisecondsMeasurer()
    private val saveEntitiesTimeMs= MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadEntitiesTimeCounter = meter.counterBuilder("jps.module.iml.entities.serializer.load.entities.ms").buildObserver()
      val saveEntitiesTimeCounter = meter.counterBuilder("jps.module.iml.entities.serializer.save.entities.ms").buildObserver()

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
}

internal open class ModuleListSerializerImpl(override val fileUrl: String,
                                             protected val context: SerializationContext,
                                             private val externalModuleListSerializer: JpsModuleListSerializer? = null)
  : JpsModuleListSerializer {
  companion object {
    internal fun createModuleEntitiesSerializer(fileUrl: VirtualFileUrl,
                                                moduleGroup: String?,
                                                source: JpsFileEntitySource,
                                                context: SerializationContext,
                                                internalModuleListSerializer: JpsModuleListSerializer? = null,
                                                externalModuleListSerializer: JpsModuleListSerializer? = null) =
      ModuleImlFileEntitiesSerializer(ModulePath(JpsPathUtil.urlToPath(fileUrl.url), moduleGroup),
                                      fileUrl, source, context,
                                      internalModuleListSerializer,
                                      externalModuleListSerializer)
  }

  override val isExternalStorage: Boolean
    get() = false

  open val componentName: String
    get() = "ProjectModuleManager"

  override val entitySourceFilter: (EntitySource) -> Boolean
    get() = {
      it is JpsFileEntitySource || it is CustomModuleEntitySource ||
      it is JpsFileDependentEntitySource && (it as? JpsImportedEntitySource)?.storedExternally != true
    }

  override fun getFileName(entity: ModuleEntity): String {
    return "${entity.name}.iml"
  }

  override fun createSerializer(internalSource: JpsFileEntitySource,
                                fileUrl: VirtualFileUrl,
                                moduleGroup: String?): JpsFileEntitiesSerializer<ModuleEntity> {
    return createModuleEntitiesSerializer(fileUrl, moduleGroup, internalSource, context, this, externalModuleListSerializer)
  }

  override fun loadFileList(reader: JpsFileContentReader, virtualFileManager: VirtualFileUrlManager): List<Pair<VirtualFileUrl, String?>> {
    val moduleManagerTag = reader.loadComponent(fileUrl, componentName) ?: return emptyList()
    return ModulePath.getPathsToModuleFiles(moduleManagerTag).map {
      virtualFileManager.getOrCreateFromUri("file://${it.path}") to it.group
    }
  }

  override fun saveEntityList(entities: Sequence<ModuleEntity>, writer: JpsFileContentWriter) {
    val entitiesToSave = entities
      .filter { moduleEntity ->
        entitySourceFilter(moduleEntity.entitySource)
        || moduleEntity.contentRoots.any { cr -> entitySourceFilter(cr.entitySource) }
        || moduleEntity.sourceRoots.any { sr -> entitySourceFilter(sr.entitySource) }
        || moduleEntity.contentRoots.any { cr -> cr.excludedUrls.any { ex -> entitySourceFilter(ex.entitySource) } }
        || moduleEntity.facets.any { entitySourceFilter(it.entitySource) }
      }
      .mapNotNullTo(ArrayList()) { module -> getSourceToSave(module)?.let { Pair(it, module) } }
      .sortedBy { it.second.name }
    val componentTag: Element?
    if (entitiesToSave.isNotEmpty()) {
      val modulesTag = Element("modules")
      entitiesToSave
        .forEach { (source, module) ->
          val moduleTag = Element("module")
          val fileUrl = getModuleFileUrl(source, module)
          moduleTag.setAttribute("fileurl", fileUrl)
          moduleTag.setAttribute("filepath", JpsPathUtil.urlToPath(fileUrl))
          module.groupPath?.let {
            moduleTag.setAttribute("group", it.path.joinToString("/"))
          }
          modulesTag.addContent(moduleTag)
        }
      componentTag = JDomSerializationUtil.createComponentElement(componentName)
      componentTag.addContent(modulesTag)
    }
    else {
      componentTag = null
    }
    writer.saveComponent(fileUrl, componentName, componentTag)
  }

  protected open fun getSourceToSave(module: ModuleEntity): JpsProjectFileEntitySource.FileInDirectory? {
    val entitySource = module.entitySource
    if (entitySource is CustomModuleEntitySource) {
      return entitySource.internalSource as? JpsProjectFileEntitySource.FileInDirectory
    }
    if (entitySource is JpsFileDependentEntitySource) {
      return entitySource.originalSource as? JpsProjectFileEntitySource.FileInDirectory
    }
    return entitySource as? JpsProjectFileEntitySource.FileInDirectory
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, TEST_MODULE_PROPERTIES_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME, null)

    manuallyRemoveImlFile(fileUrl)
  }

  // We manually remove the `.iml` file as it's not removed by component store due to IJPL-926
  // Probably there is no need to set `null` to the components, but let's do it just in case.
  // If IJPL-926 is fixed, this manual removal should go away and only `saveComponent(..., null)` should remain.
  private fun manuallyRemoveImlFile(fileUrl: String) {
    val path = Path(JpsPathUtil.urlToPath(fileUrl))
    // Check that `iml` with a correct case is removed on case-insensitive systems
    // `path.exists()` check should be done as `toRealPath` will break if the file doesn't exist.
    if (path.exists() && path.toString() == path.toRealPath().toString()) {
      path.deleteIfExists()
    }
  }

  private fun getModuleFileUrl(source: JpsProjectFileEntitySource.FileInDirectory,
                               module: ModuleEntity) = source.directory.url + "/" + module.name + ".iml"

  override fun toString(): String = "ModuleListSerializerImpl($fileUrl)"
}

fun createSourceRootsOrder(orderOfItems: List<VirtualFileUrl>, entitySource: EntitySource): SourceRootOrderEntity? {
  if (orderOfItems.size <= 1) return null

  return SourceRootOrderEntity(orderOfItems, entitySource)
}

fun ContentRootEntity.getSourceRootsComparator(): Comparator<SourceRootEntity> {
  val order = (sourceRootOrder?.orderOfSourceRoots ?: emptyList()).withIndex().associateBy({ it.value }, { it.index })
  return compareBy<SourceRootEntity> { order[it.url] ?: order.size }.thenBy { it.url.url }
}


private fun ModuleEntity.isEmpty(): Boolean {
  return this.contentRoots.isEmpty() && this.javaSettings == null && this.facets.isEmpty() && this.dependencies.filterNot { it is ModuleSourceDependency }.isEmpty()
}

