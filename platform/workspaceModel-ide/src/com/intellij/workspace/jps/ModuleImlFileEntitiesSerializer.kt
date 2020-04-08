// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.impl.ModuleManagerImpl
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.isEmpty
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.legacyBridge.intellij.toLibraryTableId
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import org.jdom.Attribute
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.StringReader

private const val MODULE_ROOT_MANAGER_COMPONENT_NAME = "NewModuleRootManager"
private const val URL_ATTRIBUTE = "url"

internal class ModuleImlFileEntitiesSerializer(internal val modulePath: ModulePath,
                                               override val fileUrl: VirtualFileUrl,
                                               override val entitySource: JpsFileEntitySource,
                                               private val serializeFacets: Boolean) : JpsFileEntitiesSerializer<ModuleEntity> {
  override val mainEntityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  override fun equals(other: Any?) = (other as? ModuleImlFileEntitiesSerializer)?.modulePath == modulePath

  override fun hashCode() = modulePath.hashCode()

  override fun loadEntities(builder: TypedEntityStorageBuilder,
                            reader: JpsFileContentReader) {
    val moduleEntity = builder.addModuleEntity(modulePath.moduleName, listOf(ModuleDependencyItem.ModuleSourceDependency), entitySource)

    val rootManagerElement = reader.loadComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME)?.clone()
    if (rootManagerElement != null) {
      loadRootManager(rootManagerElement, moduleEntity, builder)
    }

    if (serializeFacets) {
      FacetEntitiesSerializer(fileUrl, entitySource).loadFacetEntities(builder, moduleEntity, reader)
    }
  }

  private fun loadRootManager(rootManagerElement: Element,
                              moduleEntity: ModuleEntity,
                              builder: TypedEntityStorageBuilder) {
    for (contentElement in rootManagerElement.getChildrenAndDetach(CONTENT_TAG)) {
      val orderOfItems = mutableListOf<VirtualFileUrl>()
      for (sourceRootElement in contentElement.getChildren(SOURCE_FOLDER_TAG)) {
        val url = sourceRootElement.getAttributeValueStrict(URL_ATTRIBUTE)
        val isTestSource = sourceRootElement.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)?.toBoolean() == true
        val type = sourceRootElement.getAttributeValue(SOURCE_ROOT_TYPE_ATTRIBUTE)
                   ?: (if (isTestSource) JAVA_TEST_ROOT_TYPE_ID else JAVA_SOURCE_ROOT_TYPE_ID)
        val virtualFileUrl = VirtualFileUrlManager.fromUrl(url)
        orderOfItems += virtualFileUrl
        val sourceRoot = builder.addSourceRootEntity(moduleEntity, virtualFileUrl,
                                                     type == JAVA_TEST_ROOT_TYPE_ID || type == JAVA_TEST_RESOURCE_ROOT_ID,
                                                     type, entitySource)
        if (type == JAVA_SOURCE_ROOT_TYPE_ID || type == JAVA_TEST_ROOT_TYPE_ID) {
          builder.addJavaSourceRootEntity(sourceRoot, sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false,
                                          sourceRootElement.getAttributeValue(PACKAGE_PREFIX_ATTRIBUTE) ?: "", entitySource)
        }
        else if (type == JAVA_RESOURCE_ROOT_ID || type == JAVA_TEST_RESOURCE_ROOT_ID) {
          builder.addJavaResourceRootEntity(sourceRoot, sourceRootElement.getAttributeValue(IS_GENERATED_ATTRIBUTE)?.toBoolean() ?: false,
                                            sourceRootElement.getAttributeValue(RELATIVE_OUTPUT_PATH_ATTRIBUTE) ?: "", entitySource)
        }
        else {
          val elem = sourceRootElement.clone()
          elem.removeAttribute(URL_ATTRIBUTE)
          elem.removeAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE)
          builder.addCustomSourceRootPropertiesEntity(sourceRoot, JDOMUtil.write(elem), entitySource)
        }
      }
      val excludeRootsUrls = contentElement.getChildren(EXCLUDE_FOLDER_TAG)
        .map { VirtualFileUrlManager.fromUrl(it.getAttributeValueStrict(URL_ATTRIBUTE)) }
      val excludePatterns = contentElement.getChildren(EXCLUDE_PATTERN_TAG)
        .map { it.getAttributeValue(EXCLUDE_PATTERN_ATTRIBUTE) }
      val contentRootUrl = contentElement.getAttributeValueStrict(URL_ATTRIBUTE)
        .let { VirtualFileUrlManager.fromUrl(it) }
      val contentRootEntity = builder.addContentRootEntity(contentRootUrl, excludeRootsUrls, excludePatterns, moduleEntity, entitySource)

      if (orderOfItems.size > 1) {
        // Save the order in which sourceRoots appear in the module
        val orderingEntity = contentRootEntity.getSourceRootOrder()
        if (orderingEntity == null) {
          builder.addEntity(SourceRootOrderEntity::class.java, entitySource) {
            this.contentRootEntity = contentRootEntity
            this.orderOfSourceRoots = orderOfItems
          }
        }
        else {
          builder.modifyEntity(SourceRootOrderEntity::class.java, orderingEntity) {
            orderOfSourceRoots = orderOfItems
          }
        }
      }
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
    val moduleLibraryNames = mutableListOf<String>()
    val dependencyItems = rootManagerElement.getChildrenAndDetach(ORDER_ENTRY_TAG).mapTo(ArrayList()) { dependencyElement ->
      when (dependencyElement.getAttributeValue(TYPE_ATTRIBUTE)) {
        SOURCE_FOLDER_TYPE -> ModuleDependencyItem.ModuleSourceDependency
        JDK_TYPE -> ModuleDependencyItem.SdkDependency(dependencyElement.getAttributeValueStrict(JDK_NAME_ATTRIBUTE),
                                                       dependencyElement.getAttributeValue(JDK_TYPE_ATTRIBUTE))
        INHERITED_JDK_TYPE -> ModuleDependencyItem.InheritedSdkDependency
        LIBRARY_TYPE -> {
          val level = dependencyElement.getAttributeValueStrict(LEVEL_ATTRIBUTE)
          val parentId = toLibraryTableId(level)
          val libraryId = LibraryId(dependencyElement.getAttributeValueStrict(NAME_ATTRIBUTE), parentId)
          ModuleDependencyItem.Exportable.LibraryDependency(libraryId, dependencyElement.isExported(), dependencyElement.readScope())
        }
        MODULE_LIBRARY_TYPE -> {
          val libraryElement = dependencyElement.getChild(LIBRARY_TAG)!!
          // TODO. Probably we want a fixed name based on hashed library roots
          val nameAttributeValue = libraryElement.getAttributeValue(NAME_ATTRIBUTE)
          val name = LegacyBridgeLibraryImpl.generateLibraryEntityName(nameAttributeValue) { nameToCheck ->
            moduleLibraryNames.contains(nameToCheck)
          }
          moduleLibraryNames.add(name)
          val tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId())
          loadLibrary(name, libraryElement, tableId, builder, entitySource)
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
      compilerOutput = compilerOutput?.let { VirtualFileUrlManager.fromUrl(it) },
      compilerOutputForTests = compilerOutputForTests?.let { VirtualFileUrlManager.fromUrl(it) },
      module = moduleEntity,
      source = entitySource
    )
    if (!rootManagerElement.isEmpty()) {
      builder.addModuleCustomImlDataEntity(
        rootManagerTagCustomData = JDOMUtil.write(rootManagerElement),
        module = moduleEntity,
        source = entitySource
      )
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
                            entities: Map<Class<out TypedEntity>, List<TypedEntity>>,
                            writer: JpsFileContentWriter): List<TypedEntity> {
    val module = mainEntities.single()
    val savedEntities = ArrayList<TypedEntity>()
    savedEntities.add(module)
    val rootManagerElement = JDomSerializationUtil.createComponentElement(MODULE_ROOT_MANAGER_COMPONENT_NAME)

    saveJavaSettings(module.javaSettings, rootManagerElement, savedEntities)

    val customImlData = module.customImlData
    if (customImlData != null) {
      savedEntities.add(customImlData)
      val element = JDOMUtil.load(StringReader(customImlData.rootManagerTagCustomData))
      JDOMUtil.merge(rootManagerElement, element)
    }
    rootManagerElement.attributes.sortWith(knownAttributesComparator)
    //todo ensure that custom data is written in proper order

    val contentEntities = module.contentRoots.filter { it.entitySource == module.entitySource }.sortedBy { it.url.url }

    // Content.url -> (SourceRoot.url -> SourceRoot)
    val contentUrlToSourceRoots = module.sourceRoots.groupByTo(HashMap()) { sourceRoot ->
      contentEntities.find { VfsUtil.isEqualOrAncestor(it.url.url, sourceRoot.url.url) }?.url?.url ?: sourceRoot.url.url
    }.mapValues { (_, value) -> value.groupByTo(HashMap()) { it.url } }

    contentEntities.forEach { contentEntry ->
      savedEntities.add(contentEntry)
      val contentRootTag = Element(CONTENT_TAG)
      contentRootTag.setAttribute(URL_ATTRIBUTE, contentEntry.url.url)

      val sourceRoots = contentUrlToSourceRoots[contentEntry.url.url]
      if (sourceRoots != null) {
        // Save the source roots where the order is known
        contentEntry.getSourceRootOrder()?.orderOfSourceRoots?.forEach {
          sourceRoots.remove(it)?.forEach { sourceRoot ->
            contentRootTag.addContent(saveSourceRoot(sourceRoot, savedEntities))
          }
        }
        // Save the roots with unknown ordering
        sourceRoots.values.flatten().sortedBy { it.url.url }.forEach { contentRootTag.addContent(saveSourceRoot(it, savedEntities)) }
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
      rootManagerElement.addContent(saveDependencyItem(it, moduleLibraries, savedEntities))
    }

    writer.saveComponent(fileUrl.url, MODULE_ROOT_MANAGER_COMPONENT_NAME, rootManagerElement)

    if (serializeFacets) {
      @Suppress("UNCHECKED_CAST")
      val facets = entities[FacetEntity::class.java] as List<FacetEntity>? ?: emptyList()
      if (facets.isNotEmpty()) {
        FacetEntitiesSerializer(fileUrl, entitySource).saveFacetEntities(module, facets, writer)
      }
    }
    return savedEntities
  }

  private fun javaPluginPresent() = PluginManagerCore.getPlugin(PluginId.findId("com.intellij.java")) != null

  private fun saveJavaSettings(javaSettings: JavaModuleSettingsEntity?,
                               rootManagerElement: Element,
                               savedEntities: MutableList<TypedEntity>) {
    if (javaSettings == null) {
      if (javaPluginPresent()) {
        rootManagerElement.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
        rootManagerElement.addContent(Element(EXCLUDE_OUTPUT_TAG))
      }

      return
    }

    savedEntities.add(javaSettings)
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

  private fun saveDependencyItem(dependencyItem: ModuleDependencyItem, moduleLibraries: Map<String, LibraryEntity>,
                                 savedEntities: MutableList<TypedEntity>)
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
          addContent(saveLibrary(moduleLibraries.getValue(library.name), savedEntities))
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

  private fun Element.setExportedAndScopeAttributes(item: ModuleDependencyItem.Exportable) {
    if (item.exported) {
      setAttribute(EXPORTED_ATTRIBUTE, "")
    }
    if (item.scope != ModuleDependencyItem.DependencyScope.COMPILE) {
      setAttribute(SCOPE_ATTRIBUTE, item.scope.name)
    }
  }

  private fun createOrderEntryTag(type: String) = Element(ORDER_ENTRY_TAG).setAttribute(TYPE_ATTRIBUTE, type)

  private fun saveSourceRoot(sourceRoot: SourceRootEntity,
                             savedEntities: MutableList<TypedEntity>): Element {
    savedEntities.add(sourceRoot)
    val sourceRootTag = Element(SOURCE_FOLDER_TAG)
    sourceRootTag.setAttribute(URL_ATTRIBUTE, sourceRoot.url.url)
    val rootType = sourceRoot.rootType
    if (rootType !in listOf(JAVA_SOURCE_ROOT_TYPE_ID, JAVA_TEST_ROOT_TYPE_ID)) {
      sourceRootTag.setAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE, rootType)
    }
    val javaRootProperties = sourceRoot.asJavaSourceRoot()
    if (javaRootProperties != null) {
      savedEntities.add(javaRootProperties)
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
      savedEntities.add(javaResourceRootProperties)
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
      savedEntities.add(customProperties)
      val element = JDOMUtil.load(StringReader(customProperties.propertiesXmlTag))
      JDOMUtil.merge(sourceRootTag, element)
    }
    return sourceRootTag
  }

  override val additionalEntityTypes: List<Class<out TypedEntity>>
    get() = listOf(SourceRootOrderEntity::class.java)

  companion object {
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
  }
}

private const val MODULE_MANAGER_COMPONENT_NAME = "ProjectModuleManager"

internal class ModuleSerializersFactory(override val fileUrl: String, private val serializeFacets: Boolean) : JpsFileSerializerFactory<ModuleEntity> {
  override val entityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  override fun getFileName(entity: ModuleEntity): String {
    return "${entity.name}.iml"
  }

  override fun createSerializer(source: JpsFileEntitySource, fileUrl: VirtualFileUrl): JpsFileEntitiesSerializer<ModuleEntity> {
    return ModuleImlFileEntitiesSerializer(ModulePath(JpsPathUtil.urlToPath(fileUrl.filePath), null), fileUrl,  source, serializeFacets)
  }

  override fun loadFileList(reader: JpsFileContentReader): List<VirtualFileUrl> {
    val moduleManagerTag = reader.loadComponent(fileUrl, MODULE_MANAGER_COMPONENT_NAME) ?: return emptyList()
    return ModuleManagerImpl.getPathsToModuleFiles(moduleManagerTag).map {
      //todo load module groups
      File(it.path).toVirtualFileUrl()
    }
  }

  override fun saveEntitiesList(entities: Sequence<ModuleEntity>, writer: JpsFileContentWriter) {
    val componentTag = JDomSerializationUtil.createComponentElement(MODULE_MANAGER_COMPONENT_NAME)
    val entitiesToSave = entities
      .mapNotNullTo(ArrayList()) { module -> (module.entitySource as? JpsFileEntitySource.FileInDirectory)?.let { Pair(it, module) } }
      .sortedBy { it.second.name }
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
      componentTag.addContent(modulesTag)
    }

    writer.saveComponent(fileUrl, MODULE_MANAGER_COMPONENT_NAME, componentTag)
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, MODULE_ROOT_MANAGER_COMPONENT_NAME, null)
  }

  private fun getModuleFileUrl(source: JpsFileEntitySource.FileInDirectory,
                               module: ModuleEntity) = source.directory.url + "/" + module.name + ".iml"
}