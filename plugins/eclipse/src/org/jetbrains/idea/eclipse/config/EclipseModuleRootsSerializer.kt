// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.configurationStore.StorageManagerFileWriteRequestor
import com.intellij.configurationStore.getOrCreateVirtualFile
import com.intellij.configurationStore.runAsWriteActionIfNeeded
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.Function
import com.intellij.util.io.exists
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jdom.output.EclipseJDOMUtil
import org.jetbrains.idea.eclipse.AbstractEclipseClasspathReader
import org.jetbrains.idea.eclipse.AbstractEclipseClasspathReader.expandLinkedResourcesPath
import org.jetbrains.idea.eclipse.EclipseXml
import org.jetbrains.idea.eclipse.IdeaXml
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper
import org.jetbrains.idea.eclipse.conversion.EJavadocUtil
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter.addOrderEntry
import org.jetbrains.idea.eclipse.importWizard.EclipseNatureImporter
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Implements loading and saving module configuration from workspace model in '.classpath' file
 */
class EclipseModuleRootsSerializer : CustomModuleRootsSerializer, StorageManagerFileWriteRequestor {
  override val id: String
    get() = JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID

  companion object {
    private val LOG = logger<EclipseModuleRootsSerializer>()
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")
    internal val NATIVE_TYPE: LibraryRootTypeId = LibraryRootTypeId("NATIVE")
  }

  override fun createEntitySource(imlFileUrl: VirtualFileUrl,
                                  internalEntitySource: JpsFileEntitySource,
                                  customDir: String?,
                                  virtualFileManager: VirtualFileUrlManager): EntitySource? {
    val storageRootUrl = getStorageRoot(imlFileUrl, customDir, virtualFileManager)
    val classpathUrl = storageRootUrl.append(EclipseXml.CLASSPATH_FILE)
    return EclipseProjectFile(classpathUrl, internalEntitySource)
  }

  override fun loadRoots(builder: WorkspaceEntityStorageBuilder,
                         originalModuleEntity: ModuleEntity,
                         reader: JpsFileContentReader,
                         customDir: String?,
                         imlFileUrl: VirtualFileUrl,
                         internalModuleListSerializer: JpsModuleListSerializer?,
                         errorReporter: ErrorReporter,
                         virtualFileManager: VirtualFileUrlManager) {
    var moduleEntity = originalModuleEntity
    val storageRootUrl = getStorageRoot(imlFileUrl, customDir, virtualFileManager)
    val entitySource = moduleEntity.entitySource as EclipseProjectFile
    val contentRootEntity = builder.addContentRootEntity(storageRootUrl, emptyList(), emptyList(), moduleEntity)

    val classpathTag = reader.loadComponent(entitySource.classpathFile.url, "", null)
    if (classpathTag != null) {
      val relativePathResolver = ModuleRelativePathResolver(internalModuleListSerializer, reader, virtualFileManager)
      moduleEntity = loadClasspathTags(classpathTag, builder, contentRootEntity, storageRootUrl, reader, relativePathResolver, errorReporter, imlFileUrl,
                                       virtualFileManager)
    }
    else {
      builder.addJavaModuleSettingsEntity(false, true, storageRootUrl.append("bin"), null, null, moduleEntity, entitySource)
    }

    val emlUrl = getEmlFileUrl(imlFileUrl)
    val emlTag = reader.loadComponent(emlUrl, "", null)
    if (emlTag != null) {
      reader.getExpandMacroMap(imlFileUrl.url).substitute(emlTag, SystemInfo.isFileSystemCaseSensitive)
      EmlFileLoader(moduleEntity, builder, reader.getExpandMacroMap(emlUrl), virtualFileManager).loadEml(emlTag, contentRootEntity)
    }
    else {
      val javaSettings = moduleEntity.javaSettings
      if (javaSettings != null) {
        builder.modifyEntity(ModifiableJavaModuleSettingsEntity::class.java, javaSettings) {
          excludeOutput = false
        }
      }
      else {
        builder.addJavaModuleSettingsEntity(true, false, null, null, null, moduleEntity, entitySource)
      }
    }
  }

  private fun getEmlFileUrl(imlFileUrl: VirtualFileUrl) = imlFileUrl.url.removeSuffix(".iml") + EclipseXml.IDEA_SETTINGS_POSTFIX

  private fun loadClasspathTags(classpathTag: Element,
                                builder: WorkspaceEntityStorageBuilder,
                                contentRootEntity: ContentRootEntity,
                                storageRootUrl: VirtualFileUrl,
                                reader: JpsFileContentReader,
                                relativePathResolver: ModuleRelativePathResolver,
                                errorReporter: ErrorReporter,
                                imlFileUrl: VirtualFileUrl,
                                virtualUrlManager: VirtualFileUrlManager): ModuleEntity {
    fun reportError(message: String) {
      errorReporter.reportError(message, storageRootUrl.append(EclipseXml.CLASSPATH_FILE))
    }
    fun getUrlByRelativePath(path: String): VirtualFileUrl {
      return if (path.isEmpty()) storageRootUrl else storageRootUrl.append(FileUtil.toSystemIndependentName(path))
    }

    val moduleEntity = contentRootEntity.module
    fun editEclipseProperties(action: (ModifiableEclipseProjectPropertiesEntity) -> Unit) {
      val eclipseProperties = moduleEntity.eclipseProperties ?: builder.addEclipseProjectPropertiesEntity(moduleEntity,
                                                                                                          moduleEntity.entitySource)
      builder.modifyEntity(ModifiableEclipseProjectPropertiesEntity::class.java, eclipseProperties) {
        action(this)
      }
    }

    val storageRootPath = JpsPathUtil.urlToPath(storageRootUrl.url)
    val libraryNames = HashSet<String>()
    val expandMacroMap = reader.getExpandMacroMap(imlFileUrl.url)

    val sourceRoots = mutableListOf<VirtualFileUrl>()
    val dependencies = ArrayList<ModuleDependencyItem>()
    dependencies.add(ModuleDependencyItem.ModuleSourceDependency)

    classpathTag.getChildren(EclipseXml.CLASSPATHENTRY_TAG).forEachIndexed { index, entryTag ->
      val kind = entryTag.getAttributeValue(EclipseXml.KIND_ATTR)
      if (kind == null) {
        reportError("'${EclipseXml.KIND_ATTR}' attribute is missing in '${EclipseXml.CLASSPATHENTRY_TAG}' tag")
        return@forEachIndexed
      }
      val path = entryTag.getAttributeValue(EclipseXml.PATH_ATTR)
      if (path == null) {
        reportError("'${EclipseXml.PATH_ATTR}' attribute is missing in '${EclipseXml.CLASSPATHENTRY_TAG}' tag")
        return@forEachIndexed
      }
      val exported = EclipseXml.TRUE_VALUE == entryTag.getAttributeValue(EclipseXml.EXPORTED_ATTR)

      when (kind) {
        EclipseXml.SRC_KIND -> {
          if (path.startsWith("/")) {
            dependencies.add(ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(path.removePrefix("/")), exported,
                                                                              ModuleDependencyItem.DependencyScope.COMPILE, false))
          }
          else {
            val linkedPath = expandLinkedResourcesPath(storageRootPath, expandMacroMap, path)
            val srcUrl: VirtualFileUrl
            val sourceRoot = if (linkedPath == null) {
              srcUrl = getUrlByRelativePath(path)
              builder.addSourceRootEntity(contentRootEntity, srcUrl, JAVA_SOURCE_ROOT_TYPE_ID, contentRootEntity.entitySource)
            }
            else {
              srcUrl = convertToRootUrl(linkedPath, virtualUrlManager)
              editEclipseProperties {
                it.setVariable(EclipseModuleManagerImpl.SRC_LINK_PREFIX, path, srcUrl.url)
              }
              val newContentRoot = moduleEntity.contentRoots.firstOrNull { it.url == srcUrl }
                                   ?: builder.addContentRootEntity(srcUrl, emptyList(), emptyList(), moduleEntity)
              builder.addSourceRootEntity(newContentRoot, srcUrl, JAVA_SOURCE_ROOT_TYPE_ID, newContentRoot.entitySource)
            }
            builder.addJavaSourceRootEntity(sourceRoot, false, "")
            sourceRoots.add(sourceRoot.url)
            dependencies.removeIf { it is ModuleDependencyItem.ModuleSourceDependency }
            dependencies.add(ModuleDependencyItem.ModuleSourceDependency)
            editEclipseProperties {
              it.expectedModuleSourcePlace = dependencies.size - 1
              it.srcPlace[srcUrl.url] = index
            }
          }
        }
        EclipseXml.OUTPUT_KIND -> {
          val linked = expandLinkedResourcesPath(storageRootPath, expandMacroMap, path)
          val outputUrl: VirtualFileUrl
          if (linked != null) {
            outputUrl = virtualUrlManager.fromUrl(pathToUrl(linked))
            editEclipseProperties {
              it.setVariable(EclipseModuleManagerImpl.LINK_PREFIX, path, outputUrl.url)
            }
          }
          else {
            outputUrl = getUrlByRelativePath(path)
          }
          builder.addJavaModuleSettingsEntity(false, true, outputUrl, null,
                                              null, moduleEntity, contentRootEntity.entitySource)
        }
        EclipseXml.LIB_KIND -> {
          val linked = expandLinkedResourcesPath(storageRootPath, expandMacroMap, path)
          val url: VirtualFileUrl
          if (linked != null) {
            url = convertToRootUrl(linked, virtualUrlManager)
            editEclipseProperties {
              it.setVariable(EclipseModuleManagerImpl.LINK_PREFIX, path, url.url)
            }
          }
          else {
            url = convertRelativePathToUrl(path, contentRootEntity, relativePathResolver, virtualUrlManager)
          }
          editEclipseProperties {
            it.eclipseUrls.add(url)
          }
          val sourcePath = entryTag.getAttributeValue(EclipseXml.SOURCEPATH_ATTR)
          val srcUrl: VirtualFileUrl?
          if (sourcePath != null) {
            val linkedSrc = expandLinkedResourcesPath(storageRootPath, expandMacroMap, sourcePath)
            if (linkedSrc != null) {
              srcUrl = convertToRootUrl(linkedSrc, virtualUrlManager)
              editEclipseProperties {
                it.setVariable(EclipseModuleManagerImpl.SRC_LINK_PREFIX, path, srcUrl.url)
              }
            }
            else {
              srcUrl = convertRelativePathToUrl(sourcePath, contentRootEntity, relativePathResolver, virtualUrlManager)
            }
          }
          else {
            srcUrl = null
          }
          val nativeRoot = AbstractEclipseClasspathReader.getNativeLibraryRoot(entryTag)?.let {
            convertRelativePathToUrl(it, contentRootEntity, relativePathResolver, virtualUrlManager)
          }
          val name = generateUniqueLibraryName(path, libraryNames)
          val roots = createLibraryRoots(url, srcUrl, nativeRoot, entryTag, moduleEntity, relativePathResolver, virtualUrlManager)
          val libraryEntity = builder.addLibraryEntity(name, LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId()), roots,
                                                       emptyList(), contentRootEntity.entitySource)
          dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryEntity.persistentId(), exported,
                                                                             ModuleDependencyItem.DependencyScope.COMPILE))
        }
        EclipseXml.VAR_KIND -> {
          val slash = path.indexOf('/')
          if (slash == 0) {
            reportError("'${EclipseXml.PATH_ATTR}' attribute format is incorrect for '${EclipseXml.VAR_KIND}': $path")
            return@forEachIndexed
          }
          val libName = generateUniqueLibraryName(path, libraryNames)
          val url = convertVariablePathToUrl(expandMacroMap, path, 0, virtualUrlManager)
          editEclipseProperties {
            it.setVariable("", path, url.url)
          }
          val srcPath = entryTag.getAttributeValue(EclipseXml.SOURCEPATH_ATTR)
          val srcUrl: VirtualFileUrl?
          if (srcPath != null) {
            srcUrl = convertVariablePathToUrl(expandMacroMap, srcPath, AbstractEclipseClasspathReader.srcVarStart(srcPath),
                                              virtualUrlManager)
            editEclipseProperties {
              it.setVariable(EclipseModuleManagerImpl.SRC_PREFIX, srcPath, srcUrl.url)
            }
          }
          else {
            srcUrl = null
          }

          val nativeRoot = AbstractEclipseClasspathReader.getNativeLibraryRoot(entryTag)?.let {
            convertRelativePathToUrl(it, contentRootEntity, relativePathResolver, virtualUrlManager)
          }
          val roots = createLibraryRoots(url, srcUrl, nativeRoot, entryTag, moduleEntity, relativePathResolver, virtualUrlManager)
          val libraryEntity = builder.addLibraryEntity(libName, LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId()),
                                                       roots, emptyList(), contentRootEntity.entitySource)
          dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryEntity.persistentId(), exported,
                                                                             ModuleDependencyItem.DependencyScope.COMPILE))

        }
        EclipseXml.CON_KIND -> {
          if (path == EclipseXml.ECLIPSE_PLATFORM) {
            val libraryId = LibraryId(IdeaXml.ECLIPSE_LIBRARY,
                                      LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))
            dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryId, exported,
                                                                               ModuleDependencyItem.DependencyScope.COMPILE))
          }
          else if (path.startsWith(EclipseXml.JRE_CONTAINER)) {
            val jdkName = AbstractEclipseClasspathReader.getLastPathComponent(path)
            dependencies.removeIf { it is ModuleDependencyItem.SdkDependency || it == ModuleDependencyItem.InheritedSdkDependency }
            dependencies.add(if (jdkName != null) ModuleDependencyItem.SdkDependency(jdkName, IdeaXml.JAVA_SDK_TYPE)
                             else ModuleDependencyItem.InheritedSdkDependency)
          }
          else if (path.startsWith(EclipseXml.USER_LIBRARY)) {
            val libraryName = AbstractEclipseClasspathReader.getPresentableName(path)
            val globalLevel = findGlobalLibraryLevel(libraryName)
            val tableId = if (globalLevel != null) LibraryTableId.GlobalLibraryTableId(globalLevel) else LibraryTableId.ProjectLibraryTableId
            val libraryId = LibraryId(libraryName, tableId)
            dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryId, exported,
                                                                               ModuleDependencyItem.DependencyScope.COMPILE))

          }
          else if (path.startsWith(EclipseXml.JUNIT_CONTAINER)) {
            val junitName = IdeaXml.JUNIT + AbstractEclipseClasspathReader.getPresentableName(path)
            val url = EclipseClasspathReader.getJunitClsUrl(junitName.contains("4"))
            val roots = listOf(LibraryRoot(virtualUrlManager.fromUrl(url),
                                           LibraryRootTypeId.COMPILED))
            val libraryEntity = builder.addLibraryEntity(junitName, LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId()),
                                                         roots, emptyList(), contentRootEntity.entitySource)
            dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryEntity.persistentId(), exported,
                                                                               ModuleDependencyItem.DependencyScope.COMPILE))
          }
          else {
            val definedCons = EclipseNatureImporter.getAllDefinedCons()
            if (path in definedCons) {
              editEclipseProperties {
                it.knownCons.add(path)
                it.srcPlace[path] = index
              }
            }
            else {
              editEclipseProperties {
                it.unknownCons.add(path)
              }
              val libraryId = LibraryId(path, LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))
              dependencies.add(ModuleDependencyItem.Exportable.LibraryDependency(libraryId, exported,
                                                                                 ModuleDependencyItem.DependencyScope.COMPILE))
            }
          }
        }
        else -> {
          reportError("Unknown '${EclipseXml.KIND_ATTR}' in '${EclipseXml.CLASSPATHENTRY_TAG}': $kind")
        }
      }
    }
    if (dependencies.none { it is ModuleDependencyItem.SdkDependency || it == ModuleDependencyItem.InheritedSdkDependency }) {
      editEclipseProperties {
        it.forceConfigureJdk = true
        it.expectedModuleSourcePlace++
      }
      dependencies.add(0, ModuleDependencyItem.InheritedSdkDependency)
    }
    storeSourceRootsOrder(sourceRoots, contentRootEntity, builder)
    return builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      this.dependencies = dependencies
    }
  }

  private fun findGlobalLibraryLevel(libraryName: String): String? {
    val registrar = LibraryTablesRegistrar.getInstance()
    if (registrar.libraryTable.getLibraryByName(libraryName) != null) return LibraryTablesRegistrar.APPLICATION_LEVEL
    return registrar.customLibraryTables.find { it.getLibraryByName(libraryName) != null }?.tableLevel
  }

  private fun generateUniqueLibraryName(path: String, libraryNames: MutableSet<String>): String {
    val pathComponent = AbstractEclipseClasspathReader.getLastPathComponent(path)
    if (pathComponent != null && libraryNames.add(pathComponent)) return pathComponent
    val name = UniqueNameGenerator.generateUniqueName(path, libraryNames)
    libraryNames.add(name)
    return name
  }

  private fun createLibraryRoots(url: VirtualFileUrl,
                                 srcUrl: VirtualFileUrl?,
                                 nativeRoot: VirtualFileUrl?,
                                 entryTag: Element,
                                 moduleEntity: ModuleEntity,
                                 relativePathResolver: ModuleRelativePathResolver,
                                 virtualUrlManager: VirtualFileUrlManager): ArrayList<LibraryRoot> {
    val roots = ArrayList<LibraryRoot>()
    roots.add(LibraryRoot(url, LibraryRootTypeId.COMPILED))
    if (srcUrl != null) {
      roots.add(LibraryRoot(srcUrl, LibraryRootTypeId.SOURCES))
    }
    if (nativeRoot != null) {
      roots.add(LibraryRoot(nativeRoot, NATIVE_TYPE))
    }
    entryTag.getChild("attributes")?.getChildren("attribute")
      ?.filter { it.getAttributeValue("name") == EclipseXml.JAVADOC_LOCATION }
      ?.mapTo(roots) {
        LibraryRoot(convertToJavadocUrl(it.getAttributeValue("value")!!, moduleEntity, relativePathResolver, virtualUrlManager), JAVADOC_TYPE)
      }
    return roots
  }

  override fun saveRoots(module: ModuleEntity,
                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                         writer: JpsFileContentWriter,
                         customDir: String?,
                         imlFileUrl: VirtualFileUrl,
                         storage: WorkspaceEntityStorage,
                         virtualFileManager: VirtualFileUrlManager) {
    fun saveXmlFile(path: Path, root: Element) {
      //todo get rid of WriteAction here
      WriteAction.runAndWait<RuntimeException> {
        getOrCreateVirtualFile(path, this).getOutputStream(this).use {
          //todo use proper line separator
          EclipseJDOMUtil.output(root, OutputStreamWriter(it, StandardCharsets.UTF_8), //todo inline; schedule
          System.lineSeparator())
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    val contentRoots = entities[ContentRootEntity::class.java] as List<ContentRootEntity>? ?: emptyList()
    val entitySource = contentRoots.asSequence().map { it.entitySource }.filterIsInstance<EclipseProjectFile>().firstOrNull() ?: return

    val dotProjectFile = entitySource.classpathFile.toPath().parent.resolve(EclipseXml.PROJECT_FILE)
    if (!dotProjectFile.exists()) {
      val content = DotProjectFileHelper.generateProjectFileContent(ModuleTypeManager.getInstance().findByID(module.type), module.name)
      saveXmlFile(dotProjectFile, content)
    }

    val classpathFile = VirtualFileManager.getInstance().findFileByUrl(entitySource.classpathFile.url)
    val oldClasspath = classpathFile?.inputStream?.use { JDOMUtil.load(it) }
    val pathShortener = ModulePathShortener(storage)
    if (oldClasspath != null || !entities[SourceRootEntity::class.java].isNullOrEmpty() || module.dependencies.size > 2) {
      val newClasspath = saveClasspathTags(module, entities, entitySource, oldClasspath, pathShortener)
      if (oldClasspath == null || !JDOMUtil.areElementsEqual(newClasspath, oldClasspath)) {
        saveXmlFile(entitySource.classpathFile.toPath(), newClasspath)
      }
    }

    val emlFileUrl = getEmlFileUrl(imlFileUrl)
    val emlRoot = EmlFileSaver(module, entities, pathShortener, writer.getReplacePathMacroMap(imlFileUrl.url),
                               writer.getReplacePathMacroMap(emlFileUrl)).saveEml()
    if (emlRoot != null) {
      saveXmlFile(Paths.get(JpsPathUtil.urlToPath(emlFileUrl)), emlRoot)
    }
    else {
      val emlFile = VirtualFileManager.getInstance().findFileByUrl(emlFileUrl)
      if (emlFile != null) {
        runAsWriteActionIfNeeded {
          try {
            emlFile.delete(this)
          }
          catch (e: IOException) {
          }
        }
      }
    }
  }

  private fun saveClasspathTags(module: ModuleEntity,
                                entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                entitySource: EclipseProjectFile,
                                oldClasspath: Element?,
                                pathShortener: ModulePathShortener): Element {
    val classpathTag = Element(EclipseXml.CLASSPATH_TAG)
    val oldEntries = oldClasspath?.getChildren(EclipseXml.CLASSPATHENTRY_TAG)?.associateBy {
      it.getAttributeValue(EclipseXml.KIND_ATTR)!! + EclipseClasspathWriter.getJREKey(it.getAttributeValue(EclipseXml.PATH_ATTR))
    } ?: emptyMap()

    fun addClasspathEntry(kind: String, path: String?, index: Int = -1): Element {
      return addOrderEntry(kind, path, classpathTag, index, oldEntries)
    }

    val eclipseProperties = module.eclipseProperties
    @Suppress("UNCHECKED_CAST")
    val sourceRoots = entities[SourceRootEntity::class.java] as List<SourceRootEntity>? ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val moduleLibraries = (entities[LibraryEntity::class.java] as List<LibraryEntity>? ?: emptyList()).associateBy { it.name }

    for ((itemIndex, item) in module.dependencies.withIndex()) {
      when (item) {
        ModuleDependencyItem.ModuleSourceDependency -> {
          val shouldPlaceSeparately = eclipseProperties?.expectedModuleSourcePlace == itemIndex
          val comparator = module.mainContentRoot?.getSourceRootsComparator() ?: compareBy<SourceRootEntity> { it.url.url }
          for (sourceRoot in sourceRoots.sortedWith(comparator)) {
            var relativePath = convertToEclipsePath(sourceRoot.url, module, entitySource, pathShortener)
            if (sourceRoot.contentRoot.url != module.mainContentRoot?.url) {
              val linkedPath = eclipseProperties?.getVariable(EclipseModuleManagerImpl.SRC_LINK_PREFIX, sourceRoot.url.url)
              if (linkedPath != null) {
                relativePath = linkedPath
              }
            }
            val index = eclipseProperties?.srcPlace?.get(sourceRoot.url.url) ?: -1
            addClasspathEntry(EclipseXml.SRC_KIND, relativePath, if (shouldPlaceSeparately) index else -1)
          }
        }
        is ModuleDependencyItem.Exportable.ModuleDependency -> {
          val path = "/${item.module.name}"
          val oldElement = EclipseClasspathWriter.getOldElement(EclipseXml.SRC_KIND, path, oldEntries)
          val classpathEntry = addClasspathEntry(EclipseXml.SRC_KIND, path)
          if (oldElement == null) {
            EclipseClasspathWriter.setAttributeIfAbsent(classpathEntry, EclipseXml.COMBINEACCESSRULES_ATTR, EclipseXml.FALSE_VALUE)
          }
          EclipseClasspathWriter.setExported(classpathEntry, item.exported)
        }
        is ModuleDependencyItem.Exportable.LibraryDependency -> {
          val libraryName = item.library.name
          if (item.library.tableId is LibraryTableId.ModuleLibraryTableId) {
            val libraryRoots = moduleLibraries[libraryName]?.roots ?: emptyList()
            val libraryClassesRoots = libraryRoots.filter { it.type.name == OrderRootType.CLASSES.name() }
            val firstRoot = libraryClassesRoots.firstOrNull()?.url
            if (firstRoot != null) {
              val firstUrl = firstRoot.url
              if (libraryName.contains(IdeaXml.JUNIT) && firstUrl == EclipseClasspathReader.getJunitClsUrl(libraryName.contains("4"))) {
                val classpathEntry = addClasspathEntry(EclipseXml.CON_KIND,
                                               EclipseXml.JUNIT_CONTAINER + "/" + libraryName.substring(IdeaXml.JUNIT.length))
                EclipseClasspathWriter.setExported(classpathEntry, item.exported)
              }
              else {
                var newVarLibrary = false
                var link = false
                var eclipseVariablePath: String? = eclipseProperties?.getVariable("", firstUrl)
                if (eclipseVariablePath == null) {
                  eclipseVariablePath = eclipseProperties?.getVariable(EclipseModuleManagerImpl.LINK_PREFIX, firstUrl)
                  link = eclipseVariablePath != null
                }
                if (eclipseVariablePath == null && eclipseProperties?.eclipseUrls?.contains(firstRoot) != true) { //new library was added
                  newVarLibrary = true
                  eclipseVariablePath = convertToEclipsePathWithVariable(libraryClassesRoots)
                }
                var classpathEntry = if (eclipseVariablePath != null) {
                  addClasspathEntry(if (link) EclipseXml.LIB_KIND else EclipseXml.VAR_KIND, eclipseVariablePath)
                }
                else {
                  LOG.assertTrue(!StringUtil.isEmptyOrSpaces(firstUrl), "Library: $libraryName")
                  addClasspathEntry(EclipseXml.LIB_KIND, convertToEclipsePath(firstRoot, module, entitySource, pathShortener))
                }

                val srcRelativePath: String?
                var eclipseSrcVariablePath: String? = null
                var addSrcRoots = true
                val librarySourceRoots = libraryRoots.filter { it.type.name == OrderRootType.SOURCES.name() }
                val firstSrcRoot = librarySourceRoots.firstOrNull()?.url
                if (firstSrcRoot == null) {
                  srcRelativePath = null
                }
                else {
                  val firstSrcUrl = firstSrcRoot.url
                  srcRelativePath = convertToEclipsePath(firstSrcRoot, module, entitySource, pathShortener)
                  if (eclipseVariablePath != null) {
                    eclipseSrcVariablePath = eclipseProperties?.getVariable(EclipseModuleManagerImpl.SRC_PREFIX, firstSrcUrl)
                    if (eclipseSrcVariablePath == null) {
                      eclipseSrcVariablePath = eclipseProperties?.getVariable(EclipseModuleManagerImpl.SRC_LINK_PREFIX, firstSrcUrl)
                    }
                    if (eclipseSrcVariablePath == null) {
                      eclipseSrcVariablePath = convertToEclipsePathWithVariable(librarySourceRoots)
                      if (eclipseSrcVariablePath != null) {
                        eclipseSrcVariablePath = "/$eclipseSrcVariablePath"
                      }
                      else {
                        if (newVarLibrary) { //new library which cannot be replaced with vars
                          classpathEntry.detach()
                          classpathEntry = addClasspathEntry(EclipseXml.LIB_KIND,
                                                             convertToEclipsePath(firstRoot, module, entitySource, pathShortener))
                        }
                        else {
                          LOG.info("Added root $srcRelativePath (in existing var library) can't be replaced with any variable; src roots placed in .eml only")
                          addSrcRoots = false
                        }
                      }
                    }
                  }
                }
                EclipseClasspathWriter.setOrRemoveAttribute(classpathEntry, EclipseXml.SOURCEPATH_ATTR,
                                                            if (addSrcRoots) eclipseSrcVariablePath ?: srcRelativePath else null)
                EJavadocUtil.setupAttributes(classpathEntry,
                                             { convertToEclipseJavadocPath(it, module, entitySource.internalSource.projectLocation, pathShortener) },
                                             EclipseXml.JAVADOC_LOCATION,
                                             libraryRoots.filter { it.type.name == "JAVADOC" }.map { it.url }.toList().toTypedArray())
                val nativeRoots = libraryRoots.asSequence().filter { it.type.name == "NATIVE" }.map { it.url }.toList().toTypedArray()
                if (nativeRoots.isNotEmpty()) {
                  EJavadocUtil.setupAttributes(classpathEntry, Function { convertToEclipsePath(it, module, entitySource, pathShortener)!! }, EclipseXml.DLL_LINK,
                                               nativeRoots)
                }
                EclipseClasspathWriter.setExported(classpathEntry, item.exported)
              }
            }
          }
          else {
            val path = when {
              eclipseProperties?.unknownCons?.contains(libraryName) == true -> libraryName
              libraryName == IdeaXml.ECLIPSE_LIBRARY -> EclipseXml.ECLIPSE_PLATFORM
              else -> EclipseXml.USER_LIBRARY + '/' + libraryName
            }
            val classpathEntry = addClasspathEntry(EclipseXml.CON_KIND, path)
            EclipseClasspathWriter.setExported(classpathEntry, item.exported)
          }

        }
        ModuleDependencyItem.InheritedSdkDependency -> {
          if (eclipseProperties?.forceConfigureJdk != true) {
            addClasspathEntry(EclipseXml.CON_KIND, EclipseXml.JRE_CONTAINER)
          }
        }
        is ModuleDependencyItem.SdkDependency -> {
          val jdkLink = "${EclipseXml.JRE_CONTAINER}${if (item.sdkType == "JavaSDK") EclipseXml.JAVA_SDK_TYPE else ""}/${item.sdkName}"
          addClasspathEntry(EclipseXml.CON_KIND, jdkLink)
        }
      }
    }

    val compilerOutput = module.javaSettings?.compilerOutput
    val outputPath = if (compilerOutput != null) {
      val linkedPath = eclipseProperties?.getVariable(EclipseModuleManagerImpl.LINK_PREFIX, compilerOutput.url)
      linkedPath ?: convertToEclipsePath(compilerOutput, module, entitySource, pathShortener)
    }
    else {
      "bin"
    }
    eclipseProperties?.knownCons?.forEach {
      addClasspathEntry(EclipseXml.CON_KIND, it, eclipseProperties.srcPlace[it] ?: -1)
    }
    EclipseClasspathWriter.setAttributeIfAbsent(addClasspathEntry(EclipseXml.OUTPUT_KIND, outputPath), EclipseXml.PATH_ATTR,
                                                EclipseXml.BIN_DIR)

    return classpathTag
  }
}
