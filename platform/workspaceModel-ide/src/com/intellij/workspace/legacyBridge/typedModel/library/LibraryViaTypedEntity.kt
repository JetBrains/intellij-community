package com.intellij.workspace.legacyBridge.typedModel.library

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.*
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import java.io.StringReader

internal class LibraryViaTypedEntity(
  val libraryEntity: LibraryEntity,
  internal val filePointerProvider: LegacyBridgeFilePointerProvider,
  val storage: TypedEntityStorage,
  val libraryTable: LibraryTable) {
  private val roots = libraryEntity.roots.groupBy { it.type }.mapValues {(_, roots) ->
    val urls = roots.filter { it.inclusionOptions == LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }
    val jarDirs = roots
      .filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }
      .map { LegacyBridgeJarDirectory(it.url, it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY)
    }
    LegacyBridgeFileContainer(urls, jarDirs)
  }
  private val excludedRootsContainer = if (libraryEntity.excludedRoots.isNotEmpty()) LegacyBridgeFileContainer(libraryEntity.excludedRoots, emptyList()) else null

  val kind: PersistentLibraryKind<*>?
  val properties: LibraryProperties<*>?
  init {
    val customProperties = libraryEntity.getCustomProperties()
    kind = customProperties?.libraryType?.let { LibraryKind.findById(it) ?: UnknownLibraryKind.getOrCreate(it) } as? PersistentLibraryKind<*>
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
    get() = LegacyBridgeLibraryImpl.getLegacyLibraryName(libraryEntity.persistentId())

  val module: Module?
    get() = (libraryTable as? LegacyBridgeModuleLibraryTable)?.module

  fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider)
             ?.files ?: VirtualFile.EMPTY_ARRAY
  }

  fun getUrls(rootType: OrderRootType): Array<String> {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.run { urls + jarDirectories.map { it.directoryUrl } }
             ?.map { it.url }?.toTypedArray() ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  val excludedRootUrls: Array<String>
    get() = excludedRootsContainer?.getAndCacheVirtualFilePointerContainer(filePointerProvider)?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY

  val excludedRoots: Array<VirtualFile>
    get() = excludedRootsContainer?.getAndCacheVirtualFilePointerContainer(filePointerProvider)?.files ?: VirtualFile.EMPTY_ARRAY

  fun isValid(url: String, rootType: OrderRootType): Boolean {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider)
             ?.findByUrl(url)?.isValid ?: false
  }

  fun getInvalidRootUrls(type: OrderRootType): List<String> {
    return roots[LibraryRootTypeId(type.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider)
             ?.list?.filterNot { it.isValid }?.map { it.url } ?: emptyList()
  }

  fun isJarDirectory(url: String) = isJarDirectory(url, OrderRootType.CLASSES)

  fun isJarDirectory(url: String, rootType: OrderRootType): Boolean {
    return roots[LibraryRootTypeId(rootType.name())]
             ?.getAndCacheVirtualFilePointerContainer(filePointerProvider)
             ?.jarDirectories?.any { it.first == url } ?: false
  }

  // TODO Implement
  val externalSource: ProjectModelExternalSource?
    get() = null
}
