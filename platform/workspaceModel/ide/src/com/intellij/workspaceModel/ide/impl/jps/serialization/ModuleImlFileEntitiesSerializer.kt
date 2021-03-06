// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.diagnostic.AttachmentFactory
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.impl.ModuleManagerImpl
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.exists
import com.intellij.util.isEmpty
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
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
import java.nio.file.Paths
import java.util.*

private const val MODULE_ROOT_MANAGER_COMPONENT_NAME = "NewModuleRootManager"
private const val URL_ATTRIBUTE = "url"
private val STANDARD_MODULE_OPTIONS = setOf(
  "type", "external.system.id", "external.system.module.version", "external.linked.project.path", "external.linked.project.id",
  "external.root.project.path", "external.system.module.group", "external.system.module.type"
)

internal open class ModuleImlFileEntitiesSerializer(internal val modulePath: ModulePath,
                                                    override val fileUrl: VirtualFileUrl,
                                                    override val internalEntitySource: JpsFileEntitySource,
                                                    private val virtualFileManager: VirtualFileUrlManager,
                                                    private val internalModuleListSerializer: JpsModuleListSerializer? = null,
                                                    private val externalModuleListSerializer: JpsModuleListSerializer? = null,
                                                    private val externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null)
  : JpsFileEntitiesSerializer<ModuleEntity> {
  override val mainEntityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  protected open val skipLoadingIfFileDoesNotExist
    get() = false

  override fun equals(other: Any?) = other?.javaClass == javaClass && (other as ModuleImlFileEntitiesSerializer).modulePath == modulePath

  override fun hashCode() = modulePath.hashCode()

  override fun loadEntities(builder: WorkspaceEntityStorageBuilder,
                            reader: JpsFileContentReader, errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager) {
    val externalStorageEnabled = externalStorageConfigurationManager?.isEnabled ?: false
    if (!externalStorageEnabled) {
      val moduleEntity = loadModuleEntity(reader, builder, errorReporter, virtualFileManager)
      if (moduleEntity != null) createFacetSerializer().loadFacetEntities(builder, moduleEntity, reader)
    } else {
      val externalSerializer = externalModuleListSerializer?.createSerializer(internalEntitySource, fileUrl, modulePath.group) as ModuleImlFileEntitiesSerializer?
      val moduleEntity = externalSerializer?.loadModuleEntity(reader, builder, errorReporter, virtualFileManager)
                         ?: loadModuleEntity(reader, builder, errorReporter, virtualFileManager)
      if (moduleEntity != null) {
        // "res" is a temporal solution to catch the root cause of https://ea.jetbrains.com/browser/ea_problems/239676
        var res = true
        res = res && createFacetSerializer().loadFacetEntities(builder, moduleEntity, reader)
        res = res && externalSerializer?.createFacetSerializer()?.loadFacetEntities(builder, moduleEntity, reader) ?: true
        if (!res) {
          LOG.error(
            "Facets are loaded with issues",
            fileUrl.virtualFile?.let { AttachmentFactory.createAttachment(it).also { att -> att.isIncluded = true } },
            externalSerializer?.fileUrl?.virtualFile?.let { AttachmentFactory.createAttachment(it).also { att -> att.isIncluded = true } },
          )
        }
      }
    }
  }

  private fun loadModuleEntity(reader: JpsFileContentReader,
                               builder: WorkspaceEntityStorageBuilder,
                               errorReporter: ErrorReporter,
                               virtualFileManager: VirtualFileUrlManager): ModuleEntity? {
    if (skipLoadingIfFileDoesNotExist && !fileUrl.toPath().exists()) return null

    val moduleOptions: Map<String?, String?>
    val customRootsSerializer: CustomModuleRootsSerializer?
    val customDir: String?
    val externalSystemOptions: Map<String?, String?>
    val externalSystemId: String?
    val entitySource = try {
      moduleOptions = readModuleOptions(reader)
      val pair = readExternalSystemOptions(reader, moduleOptions)
      externalSystemOptions = pair.first
      externalSystemId = pair.second

      customRootsSerializer = moduleOptions[JpsProjectLoader.CLASSPATH_ATTRIBUTE]?.let { customSerializerId ->
        val serializer = CustomModuleRootsSerializer.EP_NAME.extensions().filter { it.id == customSerializerId }.findAny().orElse(null)
        if (serializer == null) {
          LOG.warn("Classpath storage provider $customSerializerId not found")
        }
        return@let serializer
      }

      customDir = moduleOptions[JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE]
      customRootsSerializer?.createEntitySource(fileUrl, internalEntitySource, customDir, virtualFileManager)
                         ?: createEntitySource(externalSystemId)
    }
    catch (e: JDOMException) {
      builder.addModuleEntity(modulePath.moduleName, listOf(ModuleDependencyItem.ModuleSourceDependency), internalEntitySource)
      throw e
    }

    val moduleEntity = builder.addModuleEntity(modulePath.moduleName, listOf(ModuleDependencyItem.ModuleSourceDependency), entitySource)
    val moduleGroup = modulePath.group
    if (moduleGroup != null) {
      builder.addModuleGroupPathEntity(moduleGroup.split('/'), moduleEntity, entitySource)
    }

    val moduleType = moduleOptions["type"]
    if (moduleType != null) {
      builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
        type = moduleType
      }
    }
    @Suppress("UNCHECKED_CAST")
    val customModuleOptions =
      moduleOptions.filter { (key, value) -> key != null && value != null && key !in STANDARD_MODULE_OPTIONS } as Map<String, String>
    if (customModuleOptions.isNotEmpty()) {
      builder.addModuleCustomImlDataEntity(null, customModuleOptions, moduleEntity, entitySource)
    }

    CUSTOM_MODULE_COMPONENT_SERIALIZER_EP.extensions().forEach {
      it.loadComponent(builder, moduleEntity, reader, fileUrl, errorReporter, virtualFileManager)
    }
    if (customRootsSerializer != null) {
      customRootsSerializer.loadRoots(builder, moduleEntity, reader, customDir, fileUrl, internalModuleListSerializer, errorReporter, virtualFileManager)
    }
    else {
      loadExternalSystemOptions(builder, moduleEntity, reader, externalSystemOptions, externalSystemId, entitySource)

      val rootManagerElement = reader.loadComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, getBaseDirPath())?.clone()
      if (rootManagerElement != null) {
        loadRootManager(rootManagerElement, moduleEntity, builder, virtualFileManager)
      }
    }

    return moduleEntity
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
    val component = reader.loadComponent(fileUrl.url, "DeprecatedModuleOptionManager", getBaseDirPath()) ?: return emptyMap()
    return component.getChildren("option").associateBy({ it.getAttributeValue("key") },
                                                       { it.getAttributeValue("value") })
  }

  protected open fun loadExternalSystemOptions(builder: WorkspaceEntityStorageBuilder,
                                               module: ModuleEntity,
                                               reader: JpsFileContentReader,
                                               externalSystemOptions: Map<String?, String?>,
                                               externalSystemId: String?,
                                               entitySource: EntitySource) {

    val optionsEntity = builder.getOrCreateExternalSystemModuleOptions(module, entitySource)
    builder.modifyEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, optionsEntity) {
      externalSystem = externalSystemId
      externalSystemModuleVersion = externalSystemOptions["external.system.module.version"]
      linkedProjectPath = externalSystemOptions["external.linked.project.path"]
      linkedProjectId = externalSystemOptions["external.linked.project.id"]
      rootProjectPath = externalSystemOptions["external.root.project.path"]
      externalSystemModuleGroup = externalSystemOptions["external.system.module.group"]
      externalSystemModuleType = externalSystemOptions["external.system.module.type"]
    }
  }

  private fun loadRootManager(rootManagerElement: Element,
                              moduleEntity: ModuleEntity,
                              builder: WorkspaceEntityStorageBuilder,
                              virtualFileManager: VirtualFileUrlManager) {
    val entitySource = moduleEntity.entitySource
    for (contentElement in rootManagerElement.getChildrenAndDetach(CONTENT_TAG)) {
      val orderOfItems = mutableListOf<VirtualFileUrl>()
      val excludeRootsUrls = contentElement.getChildren(EXCLUDE_FOLDER_TAG)
        .map { virtualFileManager.fromUrl(it.getAttributeValueStrict(URL_ATTRIBUTE)) }
      val excludePatterns = contentElement.getChildren(EXCLUDE_PATTERN_TAG)
        .map { it.getAttributeValue(EXCLUDE_PATTERN_ATTRIBUTE) }
      val contentRootUrl = contentElement.getAttributeValueStrict(URL_ATTRIBUTE)
        .let { virtualFileManager.fromUrl(it) }
      val contentRootEntity = builder.addContentRootEntity(contentRootUrl, excludeRootsUrls, excludePatterns, moduleEntity)

      for (sourceRootElement in contentElement.getChildren(SOURCE_FOLDER_TAG)) {
        val url = sourceRootElement.getAttributeValueStrict(URL_ATTRIBUTE)
        val isTestSource = sourceRootElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)?.toBoolean() == true
        val type = sourceRootElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE)
                   ?: (if (isTestSource) JAVA_TEST_ROOT_TYPE_ID else JAVA_SOURCE_ROOT_TYPE_ID)
        val virtualFileUrl = virtualFileManager.fromUrl(url)
        orderOfItems += virtualFileUrl
        val sourceRoot = builder.addSourceRootEntity(contentRootEntity, virtualFileUrl,
                                                     type == JAVA_TEST_ROOT_TYPE_ID || type == JAVA_TEST_RESOURCE_ROOT_ID,
                                                     type, entitySource)
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
          builder.addCustomSourceRootPropertiesEntity(sourceRoot, JDOMUtil.write(elem))
        }
      }

      storeSourceRootsOrder(orderOfItems, contentRootEntity, builder)
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
      when (dependencyElement.getAttributeValue(TYPE_ATTRIBUTE)) {
        SOURCE_FOLDER_TYPE -> ModuleDependencyItem.ModuleSourceDependency
        JDK_TYPE -> ModuleDependencyItem.SdkDependency(dependencyElement.getAttributeValueStrict(JDK_NAME_ATTRIBUTE),
                                                       dependencyElement.getAttributeValue(JDK_TYPE_ATTRIBUTE))
        INHERITED_JDK_TYPE -> ModuleDependencyItem.InheritedSdkDependency
        LIBRARY_TYPE -> {
          val level = dependencyElement.getAttributeValueStrict(LEVEL_ATTRIBUTE)
          val parentId = levelToLibraryTableId(level)
          val libraryId = LibraryId(dependencyElement.getAttributeValueStrict(NAME_ATTRIBUTE), parentId)
          ModuleDependencyItem.Exportable.LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
        }
        MODULE_LIBRARY_TYPE -> {
          val libraryElement = dependencyElement.getChild(LIBRARY_TAG)!!
          // TODO. Probably we want a fixed name based on hashed library roots
          val nameAttributeValue = libraryElement.getAttributeValue(NAME_ATTRIBUTE)
          val originalName = nameAttributeValue ?: "$UNNAMED_LIBRARY_NAME_PREFIX${nextUnnamedLibraryIndex++}"
          val name = generateUniqueLibraryName(originalName) { it in moduleLibraryNames }
          moduleLibraryNames.add(name)
          val tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId())
          loadLibrary(name, libraryElement, tableId, builder, entitySource, virtualFileManager)
          val libraryId = LibraryId(name, tableId)
          ModuleDependencyItem.Exportable.LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
        }
        MODULE_TYPE -> {
          val depModuleName = dependencyElement.getAttributeValueStrict(MODULE_NAME_ATTRIBUTE)
          ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(depModuleName), dependencyElement.isExported(),
                                                           dependencyElement.readScope(),
                                                           dependencyElement.getAttributeValue("production-on-test") != null)
        }
        else -> error(dependencyElement.name)
      }
    }

    if (dependencyItems.none { it is ModuleDependencyItem.ModuleSourceDependency }) {
      dependencyItems.add(ModuleDependencyItem.ModuleSourceDependency)
    }

    val inheritedCompilerOutput = rootManagerElement.getAttributeAndDetach(INHERIT_COMPILER_OUTPUT_ATTRIBUTE)
    val excludeOutput = rootManagerElement.getChildAndDetach(EXCLUDE_OUTPUT_TAG) != null
    val compilerOutput = rootManagerElement.getChildAndDetach(OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)
    val compilerOutputForTests = rootManagerElement.getChildAndDetach(TEST_OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)

    builder.addJavaModuleSettingsEntity(
      inheritedCompilerOutput = inheritedCompilerOutput?.toBoolean() ?: false,
      excludeOutput = excludeOutput,
      compilerOutput = compilerOutput?.let { virtualFileManager.fromUrl(it) },
      compilerOutputForTests = compilerOutputForTests?.let { virtualFileManager.fromUrl(it) },
      module = moduleEntity,
      source = entitySource
    )
    if (!rootManagerElement.isEmpty()) {
      val customImlData = moduleEntity.customImlData
      if (customImlData == null) {
        builder.addModuleCustomImlDataEntity(
          rootManagerTagCustomData = JDOMUtil.write(rootManagerElement),
          customModuleOptions = emptyMap(),
          module = moduleEntity,
          source = entitySource
        )
      }
      else {
        builder.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, customImlData) {
          rootManagerTagCustomData = JDOMUtil.write(rootManagerElement)
        }
      }
    }
    builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = dependencyItems
    }
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
                            storage: WorkspaceEntityStorage,
                            writer: JpsFileContentWriter) {
    val module = mainEntities.singleOrNull()
    if (module != null && acceptsSource(module.entitySource)) {
      saveModuleEntities(module, entities, storage, writer)
    }

    @Suppress("UNCHECKED_CAST")
    val facets = (entities[FacetEntity::class.java] as List<FacetEntity>?)?.filter { acceptsSource(it.entitySource) } ?: emptyList()
    createFacetSerializer().saveFacetEntities(facets, writer)
  }

  protected open fun createFacetSerializer(): FacetEntitiesSerializer {
    return FacetEntitiesSerializer(fileUrl, internalEntitySource, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, false)
  }

  protected open fun acceptsSource(entitySource: EntitySource): Boolean {
    return entitySource is JpsFileEntitySource ||
           entitySource is CustomModuleEntitySource ||
           entitySource is JpsFileDependentEntitySource && (entitySource as? JpsImportedEntitySource)?.storedExternally != true
  }

  private fun saveModuleEntities(module: ModuleEntity,
                                 entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                 storage: WorkspaceEntityStorage,
                                 writer: JpsFileContentWriter) {
    val externalSystemOptions = module.externalSystemOptions
    val customImlData = module.customImlData
    saveModuleOptions(externalSystemOptions, module.type, customImlData, writer)
    val moduleOptions = customImlData?.customModuleOptions
    val customSerializerId = moduleOptions?.get(JpsProjectLoader.CLASSPATH_ATTRIBUTE)
    if (customSerializerId != null) {
      val serializer = CustomModuleRootsSerializer.EP_NAME.extensions().filter { it.id == customSerializerId }.findAny().orElse(null)
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
    CUSTOM_MODULE_COMPONENT_SERIALIZER_EP.extensions().forEach {
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

    val contentEntities = module.contentRoots.filter { it.entitySource == module.entitySource }.sortedBy { it.url.url }

    contentEntities.forEach { contentEntry ->
      val contentRootTag = Element(CONTENT_TAG)
      contentRootTag.setAttribute(URL_ATTRIBUTE, contentEntry.url.url)

      contentEntry.sourceRoots.sortedWith(contentEntry.getSourceRootsComparator()).forEach {
        contentRootTag.addContent(saveSourceRoot(it))
      }

      contentEntry.excludedUrls.forEach {
        contentRootTag.addContent(Element(EXCLUDE_FOLDER_TAG).setAttribute(URL_ATTRIBUTE, it.url))
      }
      contentEntry.excludedPatterns.forEach {
        contentRootTag.addContent(Element(EXCLUDE_PATTERN_TAG).setAttribute(EXCLUDE_PATTERN_ATTRIBUTE, it))
      }
      rootManagerElement.addContent(contentRootTag)
    }

    @Suppress("UNCHECKED_CAST")
    val moduleLibraries = (entities[LibraryEntity::class.java] as List<LibraryEntity>? ?: emptyList()).associateBy { it.name }
    module.dependencies.forEach {
      rootManagerElement.addContent(saveDependencyItem(it, moduleLibraries))
    }

    writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootManagerElement)
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
          addContent(saveLibrary(moduleLibraries.getValue(library.name), null))
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
    val componentTag = JDomSerializationUtil.createComponentElement("DeprecatedModuleOptionManager")
    for ((name, value) in optionsMap) {
      if (value != null) {
        componentTag.addContent(Element("option").setAttribute("key", name).setAttribute("value", value))
      }
    }
    if (componentTag.children.isNotEmpty()) {
      writer.saveComponent(fileUrl.url, "DeprecatedModuleOptionManager", componentTag)
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
      sourceRootTag.setAttribute(IS_TEST_SOURCE_ATTRIBUTE, sourceRoot.tests.toString())
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
    val customProperties = sourceRoot.asCustomSourceRoot()
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
    return ModuleManagerImpl.getPathsToModuleFiles(moduleManagerTag).map {
      Paths.get(it.path).toVirtualFileUrl(virtualFileManager) to it.group
    }
  }

  override fun saveEntitiesList(entities: Sequence<ModuleEntity>, writer: JpsFileContentWriter) {
    val entitiesToSave = entities
      .filter { entitySourceFilter(it.entitySource) }
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
  }

  private fun getModuleFileUrl(source: JpsFileEntitySource.FileInDirectory,
                               module: ModuleEntity) = source.directory.url + "/" + module.name + ".iml"

  override fun toString(): String = "ModuleListSerializerImpl($fileUrl)"
}

fun storeSourceRootsOrder(orderOfItems: List<VirtualFileUrl>,
                          contentRootEntity: ContentRootEntity,
                          builder: WorkspaceEntityStorageBuilder) {
  if (orderOfItems.size > 1) {
    // Save the order in which sourceRoots appear in the module
    val orderingEntity = contentRootEntity.getSourceRootOrder()
    if (orderingEntity == null) {
      builder.addEntity(ModifiableSourceRootOrderEntity::class.java, contentRootEntity.entitySource) {
        this.contentRootEntity = contentRootEntity
        this.orderOfSourceRoots = orderOfItems
      }
    }
    else {
      builder.modifyEntity(ModifiableSourceRootOrderEntity::class.java, orderingEntity) {
        orderOfSourceRoots = orderOfItems
      }
    }
  }
}

fun ContentRootEntity.getSourceRootsComparator(): Comparator<SourceRootEntity> {
  val order = (getSourceRootOrder()?.orderOfSourceRoots ?: emptyList()).withIndex().associateBy({ it.value }, { it.index })
  return compareBy<SourceRootEntity> { order[it.url] ?: order.size }.thenBy { it.url.url }
}