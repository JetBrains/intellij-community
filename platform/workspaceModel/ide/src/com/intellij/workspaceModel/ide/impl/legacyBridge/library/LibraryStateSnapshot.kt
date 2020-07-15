package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.toExternalSource
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FileContainerDescription
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProvider
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.JarDirectoryDescription
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.getAndCacheVirtualFilePointerContainer
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridge
import com.intellij.workspaceModel.storage.bridgeEntities.*
import java.io.StringReader

internal class LibraryStateSnapshot(
  val libraryEntity: LibraryEntity,
  internal val filePointerProvider: FilePointerProvider,
  val storage: WorkspaceEntityStorage,
  val libraryTable: LibraryTable,
  val parentDisposable: Disposable) {
  private val roots = libraryEntity.roots.groupBy { it.type }.mapValues { (_, roots) ->
    val urls = roots.filter { it.inclusionOptions == LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }
    val jarDirs = roots
      .filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }
      .map {
        JarDirectoryDescription(it.url, it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY)
      }
    FileContainerDescription(urls, jarDirs)
  }
  private val excludedRootsContainer = if (libraryEntity.excludedRoots.isNotEmpty()) FileContainerDescription(libraryEntity.excludedRoots,
                                                                                                              emptyList())
  else null

  val kind: PersistentLibraryKind<*>?
  val properties: LibraryProperties<*>?

  init {
    val customProperties = libraryEntity.getCustomProperties()
    kind = customProperties?.libraryType?.let {
      LibraryKind.findById(it) ?: UnknownLibraryKind.getOrCreate(it)
    } as? PersistentLibraryKind<*>
    properties = loadProperties(kind, customProperties)
  }

  private fun loadProperties(kind: PersistentLibraryKind<*>?, customProperties: LibraryPropertiesEntity?): LibraryProperties<*>? {
    if (kind == null) return null
    val properties = kind.createDefaultProperties()
    val propertiesElement = customProperties?.propertiesXmlTag
    if (propertiesElement == null) return properties
    ComponentSerializationUtil.loadComponentState(properties, JDOMUtil.load(StringReader(propertiesElement)))
    return properties
  }

  val name: String?
    get() = LibraryBridgeImpl.getLegacyLibraryName(libraryEntity.persistentId())

  val module: Module?
    get() = (libraryTable as? ModuleLibraryTableBridge)?.module

  fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)
             ?.files ?: VirtualFile.EMPTY_ARRAY
  }

  fun getUrls(rootType: OrderRootType): Array<String> {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.run { urls + jarDirectories.map { it.directoryUrl } }
             ?.map { it.url }?.toTypedArray() ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  val excludedRootUrls: Array<String>
    get() = excludedRootsContainer?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)?.urls
            ?: ArrayUtil.EMPTY_STRING_ARRAY

  val excludedRoots: Array<VirtualFile>
    get() = excludedRootsContainer?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)?.files
            ?: VirtualFile.EMPTY_ARRAY

  fun isValid(url: String, rootType: OrderRootType): Boolean {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)
             ?.findByUrl(url)?.isValid ?: false
  }

  fun getInvalidRootUrls(type: OrderRootType): List<String> {
    return roots[LibraryRootTypeId(type.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)
             ?.list?.filterNot { it.isValid }?.map { it.url } ?: emptyList()
  }

  fun isJarDirectory(url: String) = isJarDirectory(url, OrderRootType.CLASSES)

  fun isJarDirectory(url: String, rootType: OrderRootType): Boolean {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider, parentDisposable)
             ?.jarDirectories?.any { it.first == url } ?: false
  }

  val externalSource: ProjectModelExternalSource?
    get() = (libraryEntity.entitySource as? JpsImportedEntitySource)?.toExternalSource()
}
