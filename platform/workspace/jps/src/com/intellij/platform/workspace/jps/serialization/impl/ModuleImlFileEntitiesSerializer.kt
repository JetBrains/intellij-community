// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.bridge.impl.serialization.DefaultImlNormalizer
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.xmlb.Constants.NAME
import io.opentelemetry.api.metrics.Meter
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.jps.model.serialization.*
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*
import org.jetbrains.jps.util.JpsPathUtil
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
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
  private val moduleTypes = ConcurrentFactoryMap.createMap<String, ModuleTypeId> { ModuleTypeId(it) }
  private val sourceRootTypes = ConcurrentFactoryMap.createMap<String, SourceRootTypeId> { SourceRootTypeId(it) }
  protected open val externalStorage: Boolean = false
  protected open val facetManagerComponentName: String = JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME

  override val mainEntityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  override fun equals(other: Any?) = other?.javaClass == javaClass && (other as ModuleImlFileEntitiesSerializer).modulePath == modulePath

  override fun hashCode() = modulePath.hashCode()

  internal class JpsFileContent private constructor(
    components: Map<String, Element>?,
    private val debugFileUrl: String,
    private val debugCustomModuleFilePath: String?,
  ) {
    private val components: Map<String, Element> = components ?: emptyMap()
    val jpsFileExists: Boolean = (components != null)

    fun loadComponent(componentName: String): Element? {
      return components[componentName]
    }

    companion object {
      private fun loadComponents(
        reader: JpsFileContentReader, url: String, customModuleFilePath: String?,
        transformer: ((Element) -> Unit)?,
      ): Map<String, Element>? {
        val pathStr = JpsPathUtil.urlToPath(url)
        val path = Paths.get(pathStr)
        return if (Files.isRegularFile(path)) {
          val expandMap = reader.getExpandMacroMap(customModuleFilePath ?: pathStr)
          val expander = JpsMacroExpander(expandMap)
          val rootElement = JpsComponentLoader(expander, path, /*useCache = */false).loadRootElement(path)
          if (rootElement != null) {
            transformer?.invoke(rootElement)
            val components = rootElement.children
              .filter { it.getAttributeValue(NAME_ATTRIBUTE) != null }
              .associateBy { it.getAttributeValue(NAME_ATTRIBUTE) }
            // remove component names only because ComponentStorageUtil.loadComponents do this (at this point we are not going to change the behavior).
            // It's likely, that we don't need to remove this attribute (please check JpsProjectReloadingTest)
            components.values.forEach { it.removeAttribute(NAME_ATTRIBUTE) }
            components
          }
          else {
            null
          }
        }
        else null
      }

      fun createFromXmlFile(reader: JpsFileContentReader, url: String, customModuleFilePath: String?, exceptionsCollector: MutableList<Throwable>): JpsFileContent? {
        val components = try {
          loadComponents(reader, url, customModuleFilePath, transformer = null)
        }
        catch (e: CannotLoadJpsModelException) {
          exceptionsCollector.add(e)
          null
        }
        return if (components == null) null else JpsFileContent(components, url, customModuleFilePath)
      }

      fun createFromImlFile(reader: JpsFileContentReader, url: String, customModuleFilePath: String?, exceptionsCollector: MutableList<Throwable>): JpsFileContent {
        val components = try {
          loadComponents(reader, url, customModuleFilePath, DefaultImlNormalizer::normalize)
        }
        catch (e: CannotLoadJpsModelException) {
          exceptionsCollector.add(e)
          null
        }

        return JpsFileContent(components, url, customModuleFilePath)
      }
    }
  }

  override fun loadEntities(
    reader: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager
  ): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>> = loadEntitiesTimeMs.addMeasuredTime {
    val moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity.Builder> = HashMap()
    val exceptionsCollector = ArrayList<Throwable>()

    val externalSerializer = if (context.isExternalStorageEnabled) {
      externalModuleListSerializer?.createSerializer(internalEntitySource, fileUrl, modulePath.group) as ModuleImlFileEntitiesSerializer?
    }
    else null

    val internalContent = getImlFileContent(reader, exceptionsCollector)
    val externalContent = getExternalXmlFileContent(reader, externalSerializer, exceptionsCollector)


    val moduleLoadedInfo = externalSerializer?.loadModuleEntity(externalContent, reader, errorReporter, virtualFileManager, moduleLibrariesCollector,
                                                                  exceptionsCollector)

    val moduleEntity = if (moduleLoadedInfo != null) {
      val entitySource = getOtherEntitiesEntitySource(internalContent)
      runCatchingXmlIssues(exceptionsCollector) {
        loadContentRoots(moduleLoadedInfo.customRootsSerializer, moduleLoadedInfo.moduleEntity, internalContent,
                         reader, moduleLoadedInfo.customDir, errorReporter, virtualFileManager,
                         entitySource, true, moduleLibrariesCollector)
      }
      moduleLoadedInfo.moduleEntity
    }
    else {
      val localModule = loadModuleEntity(internalContent, reader, errorReporter, virtualFileManager, moduleLibrariesCollector, exceptionsCollector)
      localModule?.moduleEntity
    }

    if (moduleEntity != null) {
      loadAdditionalContents(internalContent, virtualFileManager, moduleEntity, exceptionsCollector)

      runCatchingXmlIssues(exceptionsCollector) {
        val internalFacetTag = internalContent.loadComponent(facetManagerComponentName)
        if (internalFacetTag != null) {
          createFacetSerializer().loadFacetEntities(moduleEntity, internalFacetTag)
        }

        if (externalSerializer != null) {
          val externalFacetTag = externalContent?.loadComponent(externalSerializer.facetManagerComponentName)
          if (externalFacetTag != null) {
            externalSerializer.createFacetSerializer().loadFacetEntities(moduleEntity, externalFacetTag)
          }
        }
      }
    }

    return@addMeasuredTime LoadingResult(
      mapOf(
        ModuleEntity::class.java to listOfNotNull(moduleEntity),
        LibraryEntity::class.java to moduleLibrariesCollector.values,
      ),
      exceptionsCollector.firstOrNull(),
    )
  }

  private fun getImlFileContent(
    reader: JpsFileContentReader,
    exceptionsCollector: MutableList<Throwable>,
  ): JpsFileContent {
    if (!fileUrl.url.endsWith(".iml")) {
      LOG.error("Should be an iml file: ${fileUrl.url}")
    }
    return JpsFileContent.createFromImlFile(reader, fileUrl.url, getBaseDirPath(), exceptionsCollector)
  }

  private fun getExternalXmlFileContent(
    reader: JpsFileContentReader,
    externalSerializer: ModuleImlFileEntitiesSerializer?,
    exceptionsCollector: MutableList<Throwable>,
  ): JpsFileContent? {
    val externalFileUrl = externalSerializer?.fileUrl?.url

    return if (externalFileUrl != null) {
      if (!externalFileUrl.endsWith(".xml")) {
        LOG.error("Should be an xml file: ${fileUrl.url}")
      }
      JpsFileContent.createFromXmlFile(reader, externalFileUrl, externalSerializer.getBaseDirPath(), exceptionsCollector)
    }
    else null
  }

  private fun loadAdditionalContents(
    content: JpsFileContent,
    virtualFileManager: VirtualFileUrlManager,
    moduleEntity: ModuleEntity.Builder,
    exceptionCollector: MutableList<Throwable>,
  ) {
    val source = runCatchingXmlIssues(exceptionCollector) {
      getOtherEntitiesEntitySource(content)
    } ?: return

    val roots = runCatchingXmlIssues(exceptionCollector) {
      loadAdditionalContentRoots(moduleEntity, content, virtualFileManager, source)
    } ?: return

    moduleEntity.contentRoots += roots
  }

  override fun checkAndAddToBuilder(builder: MutableEntityStorage,
                                    orphanage: MutableEntityStorage,
                                    newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>) {

    val (orphans, elements) = newEntities.values.asSequence().flatten().partition { it.entitySource is OrphanageWorkerEntitySource }

    elements.forEach { builder addEntity it }
    orphans.forEach { orphanage addEntity it }
  }

  private class ModuleLoadedInfo(
    val moduleEntity: ModuleEntity.Builder,
    val customRootsSerializer: CustomModuleRootsSerializer?,
    val customDir: String?,
  )

  private fun loadModuleEntity(
    content: JpsFileContent?,
    readerForCustomRootsSerializerOnly: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager,
    moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity.Builder>,
    exceptionsCollector: MutableList<Throwable>,
  ): ModuleLoadedInfo? {
    if (content == null) {
      return null
    }

    val moduleOptions: Map<String?, String?>
    val customRootsSerializer: CustomModuleRootsSerializer?
    val customDir: String?
    val externalSystemOptions: Map<String?, String?>
    val externalSystemId: String?
    val entitySourceForModuleAndOtherEntities = runCatchingXmlIssues {
      moduleOptions = readModuleOptions(content)
      val pair = readExternalSystemOptions(content, moduleOptions)
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
      val customEntitySource = customRootsSerializer?.createEntitySource(fileUrl, internalEntitySource, customDir, virtualFileManager)
      val moduleEntitySource = when {
        customEntitySource != null -> customEntitySource
        // for backward compatibility: absence of file is considered as a valid module declaration.
        // *probably*, to handle cases when modules.xml has already updated from git, but corresponding iml has not (yet).
        !content.jpsFileExists -> externalSystemEntitySource
        content.loadComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME) == null -> OrphanageWorkerEntitySource
        else -> externalSystemEntitySource
      }

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
                                  internalEntitySource)
        return ModuleLoadedInfo(module, null, null)
      }
      .getOrThrow()

    val moduleEntity = ModuleEntity(modulePath.moduleName, listOf(ModuleSourceDependency),
                                    entitySourceForModuleAndOtherEntities.first)

    val entitySource = entitySourceForModuleAndOtherEntities.second
    val moduleGroup = modulePath.group
    if (moduleGroup != null) {
      ModuleGroupPathEntity(moduleGroup.split('/'), entitySource) {
        this.module = moduleEntity
      }
    }

    val moduleTypeName = moduleOptions["type"]
    if (moduleTypeName != null) {
      moduleEntity.type = moduleTypes[moduleTypeName]
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
      val componentTag = content.loadComponent(it.componentName)
      if (componentTag != null) {
        it.loadComponent(moduleEntity, componentTag, errorReporter, virtualFileManager)
      }
    }

    runCatchingXmlIssues(exceptionsCollector) {
      // Don't forget to load external system options even if custom root serializer exist
      loadExternalSystemOptions(moduleEntity, content, externalSystemOptions, externalSystemId, entitySource)

      loadContentRoots(customRootsSerializer, moduleEntity, content, readerForCustomRootsSerializerOnly, customDir, errorReporter, virtualFileManager,
                       moduleEntity.entitySource, false, moduleLibrariesCollector)
      loadTestModuleProperty(moduleEntity, content, entitySource)
    }

    return ModuleLoadedInfo(moduleEntity, customRootsSerializer, customDir)
  }

  /**
   * [loadingAdditionalRoots] - true if we load additional information of the module. For example, content roots that are defined by user
   *   in maven project.
   */
  private fun loadContentRoots(
    customRootsSerializer: CustomModuleRootsSerializer?,
    moduleEntity: ModuleEntity.Builder,
    content: JpsFileContent,
    readerForCustomRootsSerializerOnly: JpsFileContentReader,
    customDir: String?,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager,
    contentRootEntitySource: EntitySource,
    loadingAdditionalRoots: Boolean,
    moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity.Builder>,
  ) {
    if (customRootsSerializer != null) {
      customRootsSerializer.loadRoots(moduleEntity, readerForCustomRootsSerializerOnly, customDir, fileUrl, internalModuleListSerializer, errorReporter,
                                      virtualFileManager,
                                      moduleLibrariesCollector)
    }
    else {
      val rootManagerElement = content.loadComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME)?.clone()
      // MAYBE-ANK: !isEmpty is to please JpsSplitModuleAndContentRootTest on i.i.u.tests.main classpath
      // because I changed behavior of loadComponent as follows: now it does not replace empty components with nulls, but
      // honestly returns empty components. This check looks strange to me. What was the original intent here?
      if (rootManagerElement != null && !rootManagerElement.isEmpty) {
        loadRootManager(rootManagerElement, moduleEntity, virtualFileManager, contentRootEntitySource, loadingAdditionalRoots,
                        moduleLibrariesCollector)
      }
    }
  }

  private fun loadAdditionalContentRoots(
    moduleEntity: ModuleEntity.Builder,
    content: JpsFileContent,
    virtualFileManager: VirtualFileUrlManager,
    contentRootEntitySource: EntitySource,
  ): List<ContentRootEntity.Builder>? {
    val additionalElements = content.loadComponent(ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME)?.clone()
                             ?: return null
    return loadContentRootEntities(moduleEntity, additionalElements, virtualFileManager, contentRootEntitySource)
  }

  private fun getOtherEntitiesEntitySource(content: JpsFileContent): EntitySource {
    val moduleOptions = readModuleOptions(content)
    val pair = readExternalSystemOptions(content, moduleOptions)
    return createEntitySource(pair.second)
  }

  protected open fun getBaseDirPath(): String? = null

  protected open fun readExternalSystemOptions(
    content: JpsFileContent,
    moduleOptions: Map<String?, String?>,
  ): Pair<Map<String?, String?>, String?> {
    val externalSystemId = moduleOptions["external.system.id"]
                           ?: if (moduleOptions[SerializationConstants.IS_MAVEN_MODULE_IML_ATTRIBUTE] == true.toString()) SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
                           else null
    return Pair(moduleOptions, externalSystemId)
  }

  private fun readModuleOptions(content: JpsFileContent): Map<String?, String?> {
    val component = content.loadComponent(DEPRECATED_MODULE_MANAGER_COMPONENT_NAME) ?: return emptyMap()
    return component.getChildren("option").associateBy({ it.getAttributeValue("key") },
                                                       { it.getAttributeValue("value") })
  }

  protected open fun loadExternalSystemOptions(
    module: ModuleEntity.Builder,
    content: JpsFileContent,
    externalSystemOptions: Map<String?, String?>,
    externalSystemId: String?,
    entitySource: EntitySource,
  ) {
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
                              moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity.Builder>) {
    val contentRoots = loadContentRootEntities(moduleEntity, rootManagerElement, virtualFileManager, contentRootEntitySource)
    moduleEntity.contentRoots += contentRoots

    val dependencyItems = loadModuleDependencies(rootManagerElement, contentRootEntitySource, virtualFileManager, ModuleId(moduleEntity.name),
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
        customImlData.rootManagerTagCustomData = JDOMUtil.write(rootManagerElement)
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
                                     moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity.Builder>): List<Result<ModuleDependencyItem>> {
    fun Element.readScope(): DependencyScope {
      val attributeValue = getAttributeValue(SCOPE_ATTRIBUTE) ?: return DependencyScope.COMPILE
      return try {
        DependencyScope.valueOf(attributeValue)
      }
      catch (_: IllegalArgumentException) {
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

  private fun loadContentRootEntities(moduleEntity: ModuleEntity.Builder,
                                      rootManagerElement: Element,
                                      virtualFileManager: VirtualFileUrlManager,
                                      contentRootEntitySource: EntitySource): List<ContentRootEntity.Builder> {
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
        val contentRootUrl = contentRootUrlString.let { virtualFileManager.getOrCreateFromUrl(it) }
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

        contentRoot.apply {
          this.sourceRoots += sourceRoots
          this.excludedUrls += excludes
        }
        // Add order of source roots
        if (sourceRootOrder != null) {
          val existingOrder = contentRoot.sourceRootOrder
          if (existingOrder != null) {
            existingOrder.orderOfSourceRoots.addAll(sourceRootOrder.orderOfSourceRoots)
          }
          else {
            contentRoot.sourceRootOrder = sourceRootOrder
          }
        }
        // Add order of excludes
        if (excludes.size > 1) {
          val existingExcludesOrder = contentRoot.excludeUrlOrder
          if (existingExcludesOrder != null) {
            existingExcludesOrder.order.addAll(excludes.map { it.url })
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
  ): List<ExcludeUrlEntity.Builder> {
    return contentElement
      .getChildren(EXCLUDE_FOLDER_TAG)
      .map { virtualFileManager.getOrCreateFromUrl(it.getAttributeValueStrict(URL_ATTRIBUTE)) }
      .map { exclude ->
        ExcludeUrlEntity(exclude, entitySource)
      }
  }

  private fun loadSourceRoots(
    contentElement: Element,
    virtualFileManager: VirtualFileUrlManager,
    sourceRootSource: EntitySource,
  ): List<SourceRootEntity.Builder> {

    return contentElement.getChildren(SOURCE_FOLDER_TAG).map { sourceRootElement ->
      val isTestSource = sourceRootElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)?.toBoolean() == true
      val type = sourceRootElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE)
                 ?: (if (isTestSource) JAVA_TEST_ROOT_TYPE_ID else JAVA_SOURCE_ROOT_TYPE_ID)

      val sourceRoot = SourceRootEntity(
        url = virtualFileManager.getOrCreateFromUrl(sourceRootElement.getAttributeValueStrict(URL_ATTRIBUTE)),
        rootTypeId = sourceRootTypes[type]!!,
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

  private fun loadTestModuleProperty(moduleEntity: ModuleEntity.Builder, content: JpsFileContent, entitySource: EntitySource) {
    val component = content.loadComponent(TEST_MODULE_PROPERTIES_COMPONENT_NAME) ?: return
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
  override fun saveEntities(
    mainEntities: Collection<ModuleEntity>,
    entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
    storage: EntityStorage,
    writer: JpsFileContentWriter,
  ) {
    writer.saveFile(fileUrl.url) { fileContentWriter ->
      saveEntities(mainEntities, entities, storage, fileContentWriter, writer)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun saveEntities(
    mainEntities: Collection<ModuleEntity>,
    entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
    storage: EntityStorage,
    content: WritableJpsFileContent,
    writerForCustomRootsSerializerOnly: JpsFileContentWriter,
  ) = saveEntitiesTimeMs.addMeasuredTime {

    if (externalStorage && FileUtil.extensionEquals(fileUrl.url, "iml")) {
      // Trying to catch https://ea.jetbrains.com/browser/ea_problems/239676
      logger<FacetsSerializer>().error("""Incorrect file for the serializer
        |externalStorage: true
        |file path: $fileUrl
      """.trimMargin())
    }

    val module = mainEntities.singleOrNull()
    if (module != null && acceptsSource(module.entitySource)) {
      saveModuleEntities(module, entities, storage, content, writerForCustomRootsSerializerOnly)
    }
    else {
      val targetComponent = ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME
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
          content.saveComponent(targetComponent, rootElement)
        }
        if (sourceRootEntities.isNotEmpty() || excludeRoots.isNotEmpty()) {
          val excludes = excludeRoots.groupBy { it.contentRoot!!.url }.toMutableMap()
          if (sourceRootEntities.isNotEmpty()) {
            sourceRootEntities.groupBy { it.contentRoot }
              .toSortedMap(compareBy { it.url.url })
              .forEach { (contentRoot, sourceRoots) ->
                val contentRootTag = Element(CONTENT_TAG)
                contentRootTag.setAttribute(URL_ATTRIBUTE, contentRoot.url.url)
                contentRootTag.setAttribute(DUMB_ATTRIBUTE, true.toString())
                saveSourceRootEntities(sourceRoots, contentRootTag, contentRoot.getSourceRootsComparator())
                excludes[contentRoot.url]?.let {
                  saveExcludeUrls(contentRootTag, it)
                  excludes.remove(contentRoot.url)
                }
                rootElement.addContent(contentRootTag)
                content.saveComponent(targetComponent, rootElement)
              }
          }
          excludes.toSortedMap(compareBy { it.url }).forEach { (url, exclude) ->
            val contentRootTag = Element(CONTENT_TAG)
            contentRootTag.setAttribute(URL_ATTRIBUTE, url.url)
            contentRootTag.setAttribute(DUMB_ATTRIBUTE, true.toString())
            saveExcludeUrls(contentRootTag, exclude)
            rootElement.addContent(contentRootTag)
            content.saveComponent(targetComponent, rootElement)
          }
        }

        // Component to save additional roots before introducing AdditionalModuleElements.
        // It's not used for this function anymore and should be cleared
        content.saveComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
      }
      else {
        content.saveComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
        content.saveComponent(DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
        content.saveComponent(ADDITIONAL_MODULE_ELEMENTS_COMPONENT_NAME, null)
      }
    }

    val componentTag = createFacetSerializer().saveFacetEntities(module, entities, this::acceptsSource)
    componentTag?.setAttribute(NAME, facetManagerComponentName)
    content.saveComponent(facetManagerComponentName, componentTag)
  }

  private fun createFacetSerializer(): FacetsSerializer {
    return FacetsSerializer(internalEntitySource, externalStorage, context)
  }

  protected open fun acceptsSource(entitySource: EntitySource): Boolean {
    return entitySource is JpsFileEntitySource ||
           entitySource is CustomModuleEntitySource ||
           entitySource is JpsFileDependentEntitySource && (entitySource as? JpsImportedEntitySource)?.storedExternally != true
  }

  private fun saveModuleEntities(
    module: ModuleEntity,
    entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
    storage: EntityStorage,
    content: WritableJpsFileContent,
    writerForCustomRootsSerializerOnly: JpsFileContentWriter,
  ) {
    val externalSystemOptions = module.exModuleOptions
    val customImlData = module.customImlData
    saveModuleOptions(externalSystemOptions, module.type?.name, customImlData, content)
    val moduleOptions = customImlData?.customModuleOptions
    val customSerializerId = moduleOptions?.get(JpsProjectLoader.CLASSPATH_ATTRIBUTE)
    if (customSerializerId != null) {
      val serializer = context.customModuleRootsSerializers.firstOrNull { it.id == customSerializerId }
      if (serializer != null) {
        val customDir = moduleOptions[JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE]
        serializer.saveRoots(module, entities, writerForCustomRootsSerializerOnly, customDir, fileUrl, storage, context.virtualFileUrlManager)
        content.saveComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
      }
      else {
        LOG.warn("Classpath storage provider $customSerializerId not found")
      }
    }
    else {
      saveRootManagerElement(module, customImlData, entities, content)
    }
    for (it in context.customModuleComponentSerializers) {
      val componentTag = it.saveComponent(module)
      if (componentTag != null) {
        content.saveComponent(it.componentName, componentTag)
      }
    }
    saveTestModuleProperty(module, content)
  }

  private fun saveRootManagerElement(module: ModuleEntity,
                                     customImlData: ModuleCustomImlDataEntity?,
                                     entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                     content: WritableJpsFileContent) {
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

    content.saveComponent(MODULE_ROOT_MANAGER_COMPONENT_NAME, rootManagerElement)
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
                                       content: WritableJpsFileContent) {
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
      content.saveComponent(DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, componentTag)
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
    val rootType = sourceRoot.rootTypeId.name
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

  private fun saveTestModuleProperty(moduleEntity: ModuleEntity, content: WritableJpsFileContent) {
    val testProperties = moduleEntity.testProperties ?: return
    val testModulePropertyTag = Element(TEST_MODULE_PROPERTIES_COMPONENT_NAME)
    testModulePropertyTag.setAttribute(PRODUCTION_MODULE_NAME_ATTRIBUTE, testProperties.productionModuleId.presentableName)
    content.saveComponent(TEST_MODULE_PROPERTIES_COMPONENT_NAME, testModulePropertyTag)
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
      virtualFileManager.getOrCreateFromUrl("file://${it.path}") to it.group
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

fun createSourceRootsOrder(orderOfItems: List<VirtualFileUrl>, entitySource: EntitySource): SourceRootOrderEntity.Builder? {
  if (orderOfItems.size <= 1) return null

  return SourceRootOrderEntity(orderOfItems, entitySource)
}

fun ContentRootEntity.getSourceRootsComparator(): Comparator<SourceRootEntity> {
  val order = (sourceRootOrder?.orderOfSourceRoots ?: emptyList()).withIndex().associateBy({ it.value }, { it.index })
  return compareBy<SourceRootEntity> { order[it.url] ?: order.size }.thenBy { it.url.url }
}
