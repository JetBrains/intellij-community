package com.intellij.workspace.legacyBridge.typedModel.library

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryKind
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFileContainer
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProvider
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeJarDirectory
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleLibraryTable
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import com.intellij.util.ArrayUtil
import org.jdom.Element
import java.io.StringReader

class LibraryViaTypedEntity(val libraryEntity: LibraryEntity,
                            internal val filePointerProvider: LegacyBridgeFilePointerProvider,
                            val storage: TypedEntityStorage,
                            val libraryTable: LibraryTable,
                            private val modifiableModelFactory: (LibraryViaTypedEntity) -> LibraryEx.ModifiableModelEx) : LegacyBridgeLibrary, RootProvider {

  override fun getModule(): Module? = (libraryTable as? LegacyBridgeModuleLibraryTable)?.module

  private val roots = libraryEntity.roots.groupBy { it.type }.mapValues {(_, roots) ->
    val urls = roots.filter { it.inclusionOptions == LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }
    val jarDirs = roots
      .filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }
      .map { LegacyBridgeJarDirectory(it.url, it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY)
    }
    filePointerProvider.getAndCacheFileContainer(LegacyBridgeFileContainer(urls, jarDirs))
  }
  private val excludedRoots = if (libraryEntity.excludedRoots.isNotEmpty()) filePointerProvider.getAndCacheFileContainer(LegacyBridgeFileContainer(libraryEntity.excludedRoots, emptyList())) else null
  private val libraryKind = libraryEntity.getCustomProperties()?.libraryType?.let { LibraryKind.findById(it) } as? PersistentLibraryKind<*>
  private val properties = loadProperties()

  private fun loadProperties(): LibraryProperties<*>? {
    if (libraryKind == null) return null
    val properties = libraryKind.createDefaultProperties()
    val propertiesElement = libraryEntity.getCustomProperties()?.propertiesXmlTag
    if (propertiesElement == null) return properties
    ComponentSerializationUtil.loadComponentState<Any>(properties, JDOMUtil.load(StringReader(propertiesElement)))
    return properties
  }

  private var disposed = false

  override val libraryId: LibraryId
    get() = libraryEntity.persistentId()

  override fun getName(): String? = if (libraryEntity.name.startsWith(LibraryEntity.UNNAMED_LIBRARY_NAME_PREFIX)) null else libraryEntity.name

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = roots[LibraryRootTypeId(rootType.name())]?.files ?: VirtualFile.EMPTY_ARRAY

  override fun getUrls(rootType: OrderRootType): Array<String> = roots[LibraryRootTypeId(rootType.name())]?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getKind(): PersistentLibraryKind<*>? = libraryKind

  override fun getProperties(): LibraryProperties<*>? = properties

  override fun getTable() = if (libraryTable is LegacyBridgeModuleLibraryTable) null else libraryTable

  override fun getExcludedRootUrls(): Array<String> = excludedRoots?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getExcludedRoots(): Array<VirtualFile> = excludedRoots?.files ?: VirtualFile.EMPTY_ARRAY

  override fun getRootProvider() = this

  override fun isValid(url: String, rootType: OrderRootType) = roots[LibraryRootTypeId(rootType.name())]?.findByUrl(url)?.isValid ?: false

  override fun getInvalidRootUrls(type: OrderRootType): List<String>  = roots[LibraryRootTypeId(type.name())]?.list?.filterNot { it.isValid }?.map { it.url } ?: emptyList()

  override fun isJarDirectory(url: String) = isJarDirectory(url, OrderRootType.CLASSES)

  override fun isJarDirectory(url: String, rootType: OrderRootType) = roots[LibraryRootTypeId(rootType.name())]
                                                                        ?.jarDirectories?.any { it.first == url } ?: false

  override fun dispose() {
    disposed = true
  }

  override fun isDisposed() = disposed

  // TODO Implement
  override fun getExternalSource(): ProjectModelExternalSource? = null

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx = modifiableModelFactory(this)

  override fun readExternal(element: Element) = throw NotImplementedError()
  override fun writeExternal(rootElement: Element) = throw NotImplementedError()

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw NotImplementedError()
  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener, parentDisposable: Disposable) = throw NotImplementedError()
  override fun removeRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw NotImplementedError()
}
