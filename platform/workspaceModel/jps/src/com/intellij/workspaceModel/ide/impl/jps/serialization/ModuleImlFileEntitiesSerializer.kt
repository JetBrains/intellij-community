// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.JDOMUtil
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.io.exists
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryNameGenerator
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*
import org.jetbrains.jps.util.JpsPathUtil
import java.io.StringReader
import java.nio.file.Path
import java.util.*

internal const val DEPRECATED_MODULE_MANAGER_COMPONENT_NAME = "DeprecatedModuleOptionManager"
private const val MODULE_ROOT_MANAGER_COMPONENT_NAME = "NewModuleRootManager"
private const val URL_ATTRIBUTE = "url"
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
                                                    private val virtualFileManager: VirtualFileUrlManager,
                                                    internal val internalModuleListSerializer: JpsModuleListSerializer? = null,
                                                    internal val externalModuleListSerializer: JpsModuleListSerializer? = null,
                                                    private val externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null)
  : JpsFileEntitiesSerializer<ModuleEntity> {
  override val mainEntityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  protected open val skipLoadingIfFileDoesNotExist
    get() = false

  override fun equals(other: Any?) = other?.javaClass == javaClass && (other as ModuleImlFileEntitiesSerializer).modulePath == modulePath

  override fun hashCode() = modulePath.hashCode()

  override fun loadEntities(builder: MutableEntityStorage,
                            reader: JpsFileContentReader, errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager) {
    val externalStorageEnabled = externalStorageConfigurationManager?.isEnabled ?: false
    if (!externalStorageEnabled) {
      val moduleLoadedInfo = loadModuleEntity(reader, builder, errorReporter, virtualFileManager)
      if (moduleLoadedInfo != null) {
        createFacetSerializer().loadFacetEntities(builder, moduleLoadedInfo.moduleEntity, reader)
      }
    }
    else {
      val externalSerializer = externalModuleListSerializer?.createSerializer(internalEntitySource, fileUrl, modulePath.group) as ModuleImlFileEntitiesSerializer?
      val moduleLoadedInfo = externalSerializer?.loadModuleEntity(reader, builder, errorReporter, virtualFileManager)
      var moduleEntity = moduleLoadedInfo?.moduleEntity
      if (moduleLoadedInfo != null) {
        val entitySource = getOtherEntitiesEntitySource(reader)
        loadContentRoots(moduleLoadedInfo.customRootsSerializer, builder, moduleLoadedInfo.moduleEntity,
                         reader, moduleLoadedInfo.customDir, errorReporter, virtualFileManager,
                         entitySource, true)
      } else {
        moduleEntity = loadModuleEntity(reader, builder, errorReporter, virtualFileManager)?.moduleEntity
      }
      if (moduleEntity != null) {
        createFacetSerializer().loadFacetEntities(builder, moduleEntity, reader)
        externalSerializer?.createFacetSerializer()?.loadFacetEntities(builder, moduleEntity, reader)
      }
    }
  }

  private class ModuleLoadedInfo(
    val moduleEntity: ModuleEntity,
    val customRootsSerializer: CustomModuleRootsSerializer?,
    val customDir: String?,
  )

  private fun loadModuleEntity(reader: JpsFileContentReader,
                               builder: MutableEntityStorage,
                               errorReporter: ErrorReporter,
                               virtualFileManager: VirtualFileUrlManager): ModuleLoadedInfo? {
    if (skipLoadingIfFileDoesNotExist && !fileUrl.toPath().exists()) {
      return null
    }

    val moduleOptions: Map<String?, String?>
    val customRootsSerializer: CustomModuleRootsSerializer?
    val customDir: String?
    val externalSystemOptions: Map<String?, String?>
    val externalSystemId: String?
    val entitySourceForModuleAndOtherEntities = try {
      moduleOptions = readModuleOptions(reader)
      val pair = readExternalSystemOptions(reader, moduleOptions)
      externalSystemOptions = pair.first
      externalSystemId = pair.second

      customRootsSerializer = moduleOptions[JpsProjectLoader.CLASSPATH_ATTRIBUTE]?.let { customSerializerId ->
        val serializer = CustomModuleRootsSerializer.EP_NAME.extensionList.firstOrNull { it.id == customSerializerId }
        if (serializer == null) {
          errorReporter.reportError(ProjectModelBundle.message("error.message.unknown.classpath.provider", fileUrl.fileName, customSerializerId), fileUrl)
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
    catch (e: JDOMException) {
      builder.addModuleEntity(modulePath.moduleName, listOf(ModuleDependencyItem.ModuleSourceDependency), internalEntitySource)
      throw e
    }

    val moduleEntity = builder.addModuleEntity(modulePath.moduleName, listOf(ModuleDependencyItem.ModuleSourceDependency),
                                               entitySourceForModuleAndOtherEntities.first)

    val entitySource = entitySourceForModuleAndOtherEntities.second
    val moduleGroup = modulePath.group
    if (moduleGroup != null) {
      builder.addModuleGroupPathEntity(moduleGroup.split('/'), moduleEntity, entitySource)
    }

    val moduleType = moduleOptions["type"]
    if (moduleType != null) {
      builder.modifyEntity(moduleEntity) {
        type = moduleType
      }
    }
    @Suppress("UNCHECKED_CAST")
    val customModuleOptions =
      moduleOptions.filter { (key, value) -> key != null && value != null && key !in STANDARD_MODULE_OPTIONS } as Map<String, String>
    if (customModuleOptions.isNotEmpty()) {
      builder.addModuleCustomImlDataEntity(null, customModuleOptions, moduleEntity, entitySource)
    }

    CUSTOM_MODULE_COMPONENT_SERIALIZER_EP.extensionList.forEach {
      it.loadComponent(builder, moduleEntity, reader, fileUrl, errorReporter, virtualFileManager)
    }
    // Don't forget to load external system options even if custom root serializer exist
    loadExternalSystemOptions(builder, moduleEntity, reader, externalSystemOptions, externalSystemId, entitySource)
    loadContentRoots(customRootsSerializer, builder, moduleEntity, reader, customDir, errorReporter, virtualFileManager,
                     moduleEntity.entitySource, false)

    return ModuleLoadedInfo(moduleEntity, customRootsSerializer, customDir)
  }

  /**
   * [loadingAdditionalRoots] - true if we load additional information of the module. For example, content roots that are defined by user
   *   in maven project.
   */
  private fun loadContentRoots(customRootsSerializer: CustomModuleRootsSerializer?,
                               builder: MutableEntityStorage,
                               moduleEntity: ModuleEntity,
                               reader: JpsFileContentReader,
                               customDir: String?,
                               errorReporter: ErrorReporter,
                               virtualFileManager: VirtualFileUrlManager,
                               contentRootEntitySource: EntitySource,
                               loadingAdditionalRoots: Boolean) {
    if (customRootsSerializer != null) {
      customRootsSerializer.loadRoots(builder, moduleEntity, reader, customDir, fileUrl, internalModuleListSerializer, errorReporter,
                                      virtualFileManager)
    }
    else {
      val rootManagerElement = reader.loadComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, getBaseDirPath())?.clone()
      if (rootManagerElement != null) {
        loadRootManager(rootManagerElement, moduleEntity, builder, virtualFileManager, contentRootEntitySource, loadingAdditionalRoots)
      }
    }
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
                           ?: if (moduleOptions[ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY] == true.toString()) ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID
                           else null
    return Pair(moduleOptions, externalSystemId)
  }

  private fun readModuleOptions(reader: JpsFileContentReader): Map<String?, String?> {
    val component = reader.loadComponent(fileUrl.url, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, getBaseDirPath()) ?: return emptyMap()
    return component.getChildren("option").associateBy({ it.getAttributeValue("key") },
                                                       { it.getAttributeValue("value") })
  }

  protected open fun loadExternalSystemOptions(builder: MutableEntityStorage,
                                               module: ModuleEntity,
                                               reader: JpsFileContentReader,
                                               externalSystemOptions: Map<String?, String?>,
                                               externalSystemId: String?,
                                               entitySource: EntitySource) {
    if (!shouldCreateExternalSystemModuleOptions(externalSystemId, externalSystemOptions, MODULE_OPTIONS_TO_CHECK)) return
    val optionsEntity = builder.getOrCreateExternalSystemModuleOptions(module, entitySource)
    builder.modifyEntity(optionsEntity) {
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
                              moduleEntity: ModuleEntity,
                              builder: MutableEntityStorage,
                              virtualFileManager: VirtualFileUrlManager,
                              contentRotEntitySource: EntitySource,
                              loadingAdditionalRoots: Boolean) {
    val alreadyLoadedContentRoots = moduleEntity.contentRoots.associateBy { it.url.url }
    for (contentElement in rootManagerElement.getChildrenAndDetach(CONTENT_TAG)) {
      val contentRootUrlString = contentElement.getAttributeValueStrict(URL_ATTRIBUTE)

      // Either we'll load content root right now, or it was already loaded from an external storage
      var contentRoot = alreadyLoadedContentRoots[contentRootUrlString]
      if (contentRoot == null) {
        val contentRootUrl = contentRootUrlString
          .let { virtualFileManager.fromUrl(it) }
        val excludePatterns = contentElement.getChildren(EXCLUDE_PATTERN_TAG)
          .map { it.getAttributeValue(EXCLUDE_PATTERN_ATTRIBUTE) }
        contentRoot = builder.addContentRootEntity(contentRootUrl, emptyList(), excludePatterns,
                                                   moduleEntity, contentRotEntitySource)
      }

      loadSourceRoots(contentElement, virtualFileManager, builder, contentRoot, contentRotEntitySource)
      loadContentRootExcludes(contentElement, virtualFileManager, builder, contentRoot, contentRotEntitySource)
    }
    fun Element.readScope(): ModuleDependencyItem.DependencyScope {
      val attributeValue = getAttributeValue(SCOPE_ATTRIBUTE) ?: return ModuleDependencyItem.DependencyScope.COMPILE
      return try {
        ModuleDependencyItem.DependencyScope.valueOf(attributeValue)
      }
      catch (e: IllegalArgumentException) {
        ModuleDependencyItem.DependencyScope.COMPILE
      }
    }

    fun Element.isExported() = getAttributeValue(EXPORTED_ATTRIBUTE) != null
    val moduleLibraryNames = mutableSetOf<String>()
    var nextUnnamedLibraryIndex = 1
    val dependencyItems = rootManagerElement.getChildrenAndDetach(ORDER_ENTRY_TAG).mapTo(ArrayList()) { dependencyElement ->
      when (val orderEntryType = dependencyElement.getAttributeValue(TYPE_ATTRIBUTE)) {
        SOURCE_FOLDER_TYPE -> ModuleDependencyItem.ModuleSourceDependency
        JDK_TYPE -> ModuleDependencyItem.SdkDependency(dependencyElement.getAttributeValueStrict(JDK_NAME_ATTRIBUTE),
                                                       dependencyElement.getAttributeValue(JDK_TYPE_ATTRIBUTE))
        INHERITED_JDK_TYPE -> ModuleDependencyItem.InheritedSdkDependency
        LIBRARY_TYPE -> {
          val level = dependencyElement.getAttributeValueStrict(LEVEL_ATTRIBUTE)
          val parentId = LibraryNameGenerator.getLibraryTableId(level)
          val libraryId = LibraryId(dependencyElement.getAttributeValueStrict(NAME_ATTRIBUTE), parentId)
          ModuleDependencyItem.Exportable.LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
        }
        MODULE_LIBRARY_TYPE -> {
          val libraryElement = dependencyElement.getChild(LIBRARY_TAG)!!
          // TODO. Probably we want a fixed name based on hashed library roots
          val nameAttributeValue = libraryElement.getAttributeValue(NAME_ATTRIBUTE)
          val originalName = nameAttributeValue ?: "${LibraryNameGenerator.UNNAMED_LIBRARY_NAME_PREFIX}${nextUnnamedLibraryIndex++}"
          val name = LibraryNameGenerator.generateUniqueLibraryName(originalName) { it in moduleLibraryNames }
          moduleLibraryNames.add(name)
          val tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.symbolicId)
          loadLibrary(name, libraryElement, tableId, builder, contentRotEntitySource, virtualFileManager, false)
          val libraryId = LibraryId(name, tableId)
          ModuleDependencyItem.Exportable.LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
        }
        MODULE_TYPE -> {
          val depModuleName = dependencyElement.getAttributeValueStrict(MODULE_NAME_ATTRIBUTE)
          ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(depModuleName), dependencyElement.isExported(),
                                                           dependencyElement.readScope(),
                                                           dependencyElement.getAttributeValue("production-on-test") != null)
        }
        else -> throw JDOMException("Unexpected '$TYPE_ATTRIBUTE' attribute in '$ORDER_ENTRY_TAG' tag: $orderEntryType")
      }
    }

    if (dependencyItems.none { it is ModuleDependencyItem.ModuleSourceDependency }) {
      dependencyItems.add(ModuleDependencyItem.ModuleSourceDependency)
    }

    if (!loadingAdditionalRoots) {
      val inheritedCompilerOutput = rootManagerElement.getAttributeAndDetach(INHERIT_COMPILER_OUTPUT_ATTRIBUTE)
      val languageLevel = rootManagerElement.getAttributeAndDetach(MODULE_LANGUAGE_LEVEL_ATTRIBUTE)
      val excludeOutput = rootManagerElement.getChildAndDetach(EXCLUDE_OUTPUT_TAG) != null
      val compilerOutput = rootManagerElement.getChildAndDetach(OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)
      val compilerOutputForTests = rootManagerElement.getChildAndDetach(TEST_OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)

      builder.addJavaModuleSettingsEntity(
        inheritedCompilerOutput = inheritedCompilerOutput?.toBoolean() ?: false,
        excludeOutput = excludeOutput,
        compilerOutput = compilerOutput?.let { virtualFileManager.fromUrl(it) },
        compilerOutputForTests = compilerOutputForTests?.let { virtualFileManager.fromUrl(it) },
        languageLevelId = languageLevel,
        module = moduleEntity,
        source = contentRotEntitySource
      )
    }
    if (!JDOMUtil.isEmpty(rootManagerElement)) {
      val customImlData = moduleEntity.customImlData
      if (customImlData == null) {
        builder.addModuleCustomImlDataEntity(
          rootManagerTagCustomData = JDOMUtil.write(rootManagerElement),
          customModuleOptions = emptyMap(),
          module = moduleEntity,
          source = contentRotEntitySource
        )
      }
      else {
        builder.modifyEntity(customImlData) {
          rootManagerTagCustomData = JDOMUtil.write(rootManagerElement)
        }
      }
    }
    if (!loadingAdditionalRoots) {
      builder.modifyEntity(moduleEntity) {
        dependencies = dependencyItems
      }
    }
  }

  private fun loadContentRootExcludes(
    contentElement: Element,
    virtualFileManager: VirtualFileUrlManager,
    builder: MutableEntityStorage,
    contentRootEntity: ContentRootEntity,
    entitySource: EntitySource,
  ) {
    contentElement
      .getChildren(EXCLUDE_FOLDER_TAG)
      .map { virtualFileManager.fromUrl(it.getAttributeValueStrict(URL_ATTRIBUTE)) }
      .forEach { exclude ->
        builder addEntity ExcludeUrlEntity(exclude, entitySource) {
          this.contentRoot = contentRootEntity
        }
      }
  }

  private fun loadSourceRoots(contentElement: Element,
                              virtualFileManager: VirtualFileUrlManager,
                              builder: MutableEntityStorage,
                              contentRootEntity: ContentRootEntity,
                              sourceRootSource: EntitySource) {
    val orderOfItems = mutableListOf<VirtualFileUrl>()
    for (sourceRootElement in contentElement.getChildren(SOURCE_FOLDER_TAG)) {
      val url = sourceRootElement.getAttributeValueStrict(URL_ATTRIBUTE)
      val isTestSource = sourceRootElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)?.toBoolean() == true
      val type = sourceRootElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE)
                 ?: (if (isTestSource) JAVA_TEST_ROOT_TYPE_ID else JAVA_SOURCE_ROOT_TYPE_ID)
      val virtualFileUrl = virtualFileManager.fromUrl(url)
      orderOfItems += virtualFileUrl
      val sourceRoot = builder.addSourceRootEntity(contentRootEntity, virtualFileUrl,
                                                   type, sourceRootSource)
      if (type == JAVA_SOURCE_ROOT_TYPE_ID || type == JAVA_TEST_ROOT_TYPE_ID) {
        builder.addJavaSourceRootEntity(sourceRoot, sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false,
                                        sourceRootElement.getAttributeValue(PACKAGE_PREFIX_ATTRIBUTE) ?: "")
      }
      else if (type == JAVA_RESOURCE_ROOT_ID || type == JAVA_TEST_RESOURCE_ROOT_ID) {
        builder.addJavaResourceRootEntity(sourceRoot, sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false,
                                          sourceRootElement.getAttributeValue(RELATIVE_OUTPUT_PATH_ATTRIBUTE) ?: "")
      }
      else {
        val elem = sourceRootElement.clone()
        elem.removeAttribute(URL_ATTRIBUTE)
        elem.removeAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE)
        if (!JDOMUtil.isEmpty(elem)) {
          builder.addCustomSourceRootPropertiesEntity(sourceRoot, JDOMUtil.write(elem))
        }
      }
    }

    storeSourceRootsOrder(orderOfItems, contentRootEntity, builder)
  }

  private fun Element.getChildrenAndDetach(cname: String): List<Element> {
    val result = getChildren(cname).toList()
    result.forEach { it.detach() }
    return result
  }

  private fun Element.getAttributeAndDetach(name: String): String? {
    val result = getAttributeValue(name)
    removeAttribute(name)
    return result
  }

  private fun Element.getChildAndDetach(cname: String): Element? =
    getChild(cname)?.also { it.detach() }

  override fun saveEntities(mainEntities: Collection<ModuleEntity>,
                            entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage,
                            writer: JpsFileContentWriter) {
    val module = mainEntities.singleOrNull()
    if (module != null && acceptsSource(module.entitySource)) {
      saveModuleEntities(module, entities, storage, writer)
    }
    else if (ContentRootEntity::class.java in entities || SourceRootEntity::class.java in entities || ExcludeUrlEntity::class.java in entities) {
      val contentEntities = entities[ContentRootEntity::class.java] as? List<ContentRootEntity> ?: emptyList()
      val sourceRootEntities = (entities[SourceRootEntity::class.java] as? List<SourceRootEntity>)?.toMutableSet() ?: mutableSetOf()
      val excludeRoots = (entities[ExcludeUrlEntity::class.java] as? List<ExcludeUrlEntity>)?.filter { it.contentRoot != null }?.toMutableSet()
                         ?: mutableSetOf()
      val rootElement = JDomSerializationUtil.createComponentElement(MODULE_ROOT_MANAGER_COMPONENT_NAME)
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
        writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootElement)
      }
      if (sourceRootEntities.isNotEmpty() || excludeRoots.isNotEmpty()) {
        val excludes = excludeRoots.groupBy { it.contentRoot!!.url }.toMutableMap()
        if (sourceRootEntities.isNotEmpty()) {
          sourceRootEntities.groupBy { it.contentRoot }.forEach { contentRoot, sourceRoots ->
            val contentRootTag = Element(CONTENT_TAG)
            contentRootTag.setAttribute(URL_ATTRIBUTE, contentRoot.url.url)
            saveSourceRootEntities(sourceRoots, contentRootTag, contentRoot.getSourceRootsComparator())
            excludes[contentRoot.url]?.let {
              saveExcludeUrls(contentRootTag, it)
              excludes.remove(contentRoot.url)
            }
            rootElement.addContent(contentRootTag)
            writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootElement)
          }
        }
        excludes.forEach { url, exclude ->
          val contentRootTag = Element(CONTENT_TAG)
          contentRootTag.setAttribute(URL_ATTRIBUTE, url.url)
          saveExcludeUrls(contentRootTag, exclude)
          rootElement.addContent(contentRootTag)
          writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootElement)
        }
      }

    }
    else {
      writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
      writer.saveComponent(fileUrl.url, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
    }

    createFacetSerializer().saveFacetEntities(module, entities, writer, this::acceptsSource)
  }

  protected open fun createFacetSerializer(): FacetsSerializer {
    return FacetsSerializer(fileUrl, internalEntitySource, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, null, false)
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
      val serializer = CustomModuleRootsSerializer.EP_NAME.extensionList.firstOrNull { it.id == customSerializerId }
      if (serializer != null) {
        val customDir = moduleOptions[JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE]
        serializer.saveRoots(module, entities, writer, customDir, fileUrl, storage, virtualFileManager)
        writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
      }
      else {
        LOG.warn("Classpath storage provider $customSerializerId not found")
      }
    }
    else {
      saveRootManagerElement(module, customImlData, entities, writer)
    }
    for (it in CUSTOM_MODULE_COMPONENT_SERIALIZER_EP.extensionList) {
      it.saveComponent(module, fileUrl, writer)
    }
  }

  private fun saveRootManagerElement(module: ModuleEntity,
                                     customImlData: ModuleCustomImlDataEntity?,
                                     entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                     writer: JpsFileContentWriter) {
    val rootManagerElement = JDomSerializationUtil.createComponentElement(MODULE_ROOT_MANAGER_COMPONENT_NAME)
    saveJavaSettings(module.javaSettings, rootManagerElement)

    if (customImlData != null) {
      val rootManagerTagCustomData = customImlData.rootManagerTagCustomData
      if (rootManagerTagCustomData != null) {
        val element = JDOMUtil.load(StringReader(rootManagerTagCustomData))
        JDOMUtil.merge(rootManagerElement, element)
      }
    }
    rootManagerElement.attributes.sortWith(knownAttributesComparator)
    //todo ensure that custom data is written in proper order

    val contentEntities = module.contentRoots.filter { acceptsSource(it.entitySource) }.sortedBy { it.url.url }

    saveContentEntities(contentEntities, rootManagerElement)

    @Suppress("UNCHECKED_CAST")
    val moduleLibraries = (entities[LibraryEntity::class.java] as List<LibraryEntity>? ?: emptyList()).associateBy { it.name }
    module.dependencies.forEach {
      rootManagerElement.addContent(saveDependencyItem(it, moduleLibraries))
    }

    writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootManagerElement)
  }

  private fun saveContentEntities(contentEntities: List<ContentRootEntity>,
                                  rootManagerElement: Element) {
    contentEntities.forEach { contentEntry ->
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

  private fun saveExcludeUrls(contentRootTag: Element,
                              excludeUrls: List<ExcludeUrlEntity>) {
    excludeUrls.forEach {
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

  protected open fun createExternalEntitySource(externalSystemId: String): EntitySource
    = JpsImportedEntitySource(internalEntitySource, externalSystemId, false)


  private fun javaPluginPresent() = PluginManagerCore.getPlugin(PluginId.findId("com.intellij.java")) != null

  private fun saveJavaSettings(javaSettings: JavaModuleSettingsEntity?,
                               rootManagerElement: Element) {
    if (javaSettings == null) {
      if (javaPluginPresent()) {
        rootManagerElement.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
        rootManagerElement.addContent(Element(EXCLUDE_OUTPUT_TAG))
      }

      return
    }

    if (javaSettings.inheritedCompilerOutput) {
      rootManagerElement.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
    }
    else {
      val outputUrl = javaSettings.compilerOutput?.url
      if (outputUrl != null) {
        rootManagerElement.addContent(Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl))
      }
      val testOutputUrl = javaSettings.compilerOutputForTests?.url
      if (testOutputUrl != null) {
        rootManagerElement.addContent(Element(TEST_OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, testOutputUrl))
      }
    }
    if (javaSettings.excludeOutput) {
      rootManagerElement.addContent(Element(EXCLUDE_OUTPUT_TAG))
    }
    javaSettings.languageLevelId?.let {
      rootManagerElement.setAttribute(MODULE_LANGUAGE_LEVEL_ATTRIBUTE, it)
    }
  }

  private fun saveDependencyItem(dependencyItem: ModuleDependencyItem, moduleLibraries: Map<String, LibraryEntity>)
    = when (dependencyItem) {
    is ModuleDependencyItem.ModuleSourceDependency -> createOrderEntryTag(SOURCE_FOLDER_TYPE).setAttribute("forTests", "false")
    is ModuleDependencyItem.SdkDependency -> createOrderEntryTag(JDK_TYPE).apply {
      setAttribute(JDK_NAME_ATTRIBUTE, dependencyItem.sdkName)

      val sdkType = dependencyItem.sdkType
      setAttribute(JDK_TYPE_ATTRIBUTE, sdkType)
    }
    is ModuleDependencyItem.InheritedSdkDependency -> createOrderEntryTag(INHERITED_JDK_TYPE)
    is ModuleDependencyItem.Exportable.LibraryDependency -> {
      val library = dependencyItem.library
      if (library.tableId is LibraryTableId.ModuleLibraryTableId) {
        createOrderEntryTag(MODULE_LIBRARY_TYPE).apply {
          setExportedAndScopeAttributes(dependencyItem)
          addContent(saveLibrary(moduleLibraries.getValue(library.name), null, false))
        }
      } else {
        createOrderEntryTag(LIBRARY_TYPE).apply {
          setExportedAndScopeAttributes(dependencyItem)
          setAttribute(NAME_ATTRIBUTE, library.name)
          setAttribute(LEVEL_ATTRIBUTE, library.tableId.level)
        }
      }
    }
    is ModuleDependencyItem.Exportable.ModuleDependency -> createOrderEntryTag(MODULE_TYPE).apply {
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
      if (externalSystemOptions.externalSystem == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID) {
        optionsMap[ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY] = true.toString()
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

  private fun Element.setExportedAndScopeAttributes(item: ModuleDependencyItem.Exportable) {
    if (item.exported) {
      setAttribute(EXPORTED_ATTRIBUTE, "")
    }
    if (item.scope != ModuleDependencyItem.DependencyScope.COMPILE) {
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

    private val CUSTOM_MODULE_COMPONENT_SERIALIZER_EP = ExtensionPointName.create<CustomModuleComponentSerializer>("com.intellij.workspaceModel.customModuleComponentSerializer")
  }
}

internal open class ModuleListSerializerImpl(override val fileUrl: String,
                                             private val virtualFileManager: VirtualFileUrlManager,
                                             private val externalModuleListSerializer: JpsModuleListSerializer? = null,
                                             private val externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null)
  : JpsModuleListSerializer {
  companion object {
    internal fun createModuleEntitiesSerializer(fileUrl: VirtualFileUrl,
                                                moduleGroup: String?,
                                                source: JpsFileEntitySource,
                                                virtualFileManager: VirtualFileUrlManager,
                                                internalModuleListSerializer: JpsModuleListSerializer? = null,
                                                externalModuleListSerializer: JpsModuleListSerializer? = null,
                                                externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null) =
      ModuleImlFileEntitiesSerializer(ModulePath(JpsPathUtil.urlToPath(fileUrl.url), moduleGroup),
                                      fileUrl, source, virtualFileManager,
                                      internalModuleListSerializer,
                                      externalModuleListSerializer,
                                      externalStorageConfigurationManager)
  }

  override val isExternalStorage: Boolean
    get() = false

  open val componentName: String
    get() = "ProjectModuleManager"

  override val entitySourceFilter: (EntitySource) -> Boolean
    get() = { it is JpsFileEntitySource || it is CustomModuleEntitySource ||
              it is JpsFileDependentEntitySource && (it as? JpsImportedEntitySource)?.storedExternally != true }

  override fun getFileName(entity: ModuleEntity): String {
    return "${entity.name}.iml"
  }

  override fun createSerializer(internalSource: JpsFileEntitySource, fileUrl: VirtualFileUrl, moduleGroup: String?): JpsFileEntitiesSerializer<ModuleEntity> {
    return createModuleEntitiesSerializer(fileUrl, moduleGroup, internalSource, virtualFileManager, this, externalModuleListSerializer,
                                          externalStorageConfigurationManager)
  }

  override fun loadFileList(reader: JpsFileContentReader, virtualFileManager: VirtualFileUrlManager): List<Pair<VirtualFileUrl, String?>> {
    val moduleManagerTag = reader.loadComponent(fileUrl, componentName) ?: return emptyList()
    return ModuleManagerBridgeImpl.getPathsToModuleFiles(moduleManagerTag).map {
      Path.of(it.path).toVirtualFileUrl(virtualFileManager) to it.group
    }
  }

  override fun saveEntitiesList(entities: Sequence<ModuleEntity>, writer: JpsFileContentWriter) {
    val entitiesToSave = entities
      .filter {
        entitySourceFilter(it.entitySource)
        || it.contentRoots.any { cr -> entitySourceFilter(cr.entitySource) }
        || it.sourceRoots.any { sr -> entitySourceFilter(sr.entitySource) }
        || it.contentRoots.flatMap { cr -> cr.excludedUrls }.any { ex -> entitySourceFilter(ex.entitySource) }
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

  protected open fun getSourceToSave(module: ModuleEntity): JpsFileEntitySource.FileInDirectory? {
    val entitySource = module.entitySource
    if (entitySource is CustomModuleEntitySource) {
      return entitySource.internalSource as? JpsFileEntitySource.FileInDirectory
    }
    if (entitySource is JpsFileDependentEntitySource) {
      return entitySource.originalSource as? JpsFileEntitySource.FileInDirectory
    }
    return entitySource as? JpsFileEntitySource.FileInDirectory
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
    writer.saveComponent(fileUrl, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
  }

  private fun getModuleFileUrl(source: JpsFileEntitySource.FileInDirectory,
                               module: ModuleEntity) = source.directory.url + "/" + module.name + ".iml"

  override fun toString(): String = "ModuleListSerializerImpl($fileUrl)"
}

fun storeSourceRootsOrder(orderOfItems: MutableList<VirtualFileUrl>,
                          contentRootEntity: ContentRootEntity,
                          builder: MutableEntityStorage) {
  if (orderOfItems.size > 1) {
    // Save the order in which sourceRoots appear in the module
    val orderingEntity = contentRootEntity.sourceRootOrder
    if (orderingEntity == null) {
      builder.addEntity(SourceRootOrderEntity(orderOfItems, contentRootEntity.entitySource) {
        this.contentRootEntity = contentRootEntity
      })
    }
    else {
      builder.modifyEntity(orderingEntity) {
        orderOfSourceRoots = orderOfItems
      }
    }
  }
}

fun ContentRootEntity.getSourceRootsComparator(): Comparator<SourceRootEntity> {
  val order = (sourceRootOrder?.orderOfSourceRoots ?: emptyList()).withIndex().associateBy({ it.value }, { it.index })
  return compareBy<SourceRootEntity> { order[it.url] ?: order.size }.thenBy { it.url.url }
}