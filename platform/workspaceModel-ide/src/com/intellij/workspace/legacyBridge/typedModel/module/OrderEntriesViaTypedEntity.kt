package com.intellij.workspace.legacyBridge.typedModel.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ClonableOrderEntry
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.impl.RootModelImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.workspace.api.LibraryTableId
import com.intellij.workspace.api.ModuleDependencyItem
import com.intellij.workspace.api.ModuleEntity
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import com.intellij.util.ArrayUtil
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

abstract class OrderEntryViaTypedEntity(
  protected val model: RootModelViaTypedEntityImpl,
  private val index: Int,
  val item: ModuleDependencyItem,
  private val itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntry {

  protected val updater: ((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit
    get() = itemUpdater ?: error("This mode is read-only. Call from a modifiable model")

  override fun getOwnerModule() = model.module
  override fun compareTo(other: OrderEntry?) = index.compareTo((other as OrderEntryViaTypedEntity).index)
  override fun isValid() = true
  override fun isSynthetic() = false
}

abstract class ExportableOrderEntryViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  exportableDependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
): OrderEntryViaTypedEntity(model, index, exportableDependencyItem, itemUpdater), ExportableOrderEntry {

  private var exportedVar = exportableDependencyItem.exported
  private var scopeVar = exportableDependencyItem.scope

  override fun isExported() = exportedVar
  override fun setExported(value: Boolean) {
    if (exportedVar == value) return
    updater { (it as ModuleDependencyItem.Exportable).withExported(value) }
    exportedVar = value
  }

  override fun getScope() = scopeVar.toDependencyScope()
  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater { (it as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope()) }
    scopeVar = scope.toEntityDependencyScope()
  }
}

abstract class ModuleOrderEntryBaseViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  dependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryViaTypedEntity(model, index, dependencyItem, itemUpdater), ModuleOrderEntry, ClonableOrderEntry {

  override fun getFiles(type: OrderRootType): Array<VirtualFile> {
    return getEnumerator(type)?.roots ?: VirtualFile.EMPTY_ARRAY
  }

  override fun getUrls(rootType: OrderRootType): Array<String> {
    return getEnumerator(rootType)?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  private fun getEnumerator(rootType: OrderRootType) = module?.let { ModuleRootManagerImpl.getCachingEnumeratorForType(rootType, it) }

  override fun getPresentableName() = moduleName

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitModuleOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: RootModelImpl,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager): OrderEntry {
    TODO("not implemented")
  }
}

class ModuleOrderEntryViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  private val moduleDependencyItem: ModuleDependencyItem.Exportable.ModuleDependency,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ModuleOrderEntryBaseViaTypedEntity(model, index, moduleDependencyItem, itemUpdater) {

  private var productionOnTestVar = moduleDependencyItem.productionOnTest

  override fun getModule(): Module? {
    // TODO It's better to resolve modules via id when it'll be possible
    // val module = moduleDependencyItem.module.resolve(model.storage)?.findModule(model.module.project)
    val module = ModuleManager.getInstance(model.module.project).findModuleByName(moduleName)
    return model.accessor.getModule(module, moduleName)
  }

  override fun getModuleName() = moduleDependencyItem.module.name

  override fun isProductionOnTestDependency() = productionOnTestVar

  override fun setProductionOnTestDependency(productionOnTestDependency: Boolean) {
    if (productionOnTestVar == productionOnTestDependency) return
    updater { item -> (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency) }
    productionOnTestVar = productionOnTestDependency
  }
}

private fun ModuleEntity.findModule(project: Project) = ModuleManager.getInstance(project).findModuleByName(name)

private fun ModuleDependencyItem.DependencyScope.toDependencyScope() = when (this) {
  ModuleDependencyItem.DependencyScope.COMPILE -> DependencyScope.COMPILE
  ModuleDependencyItem.DependencyScope.RUNTIME -> DependencyScope.RUNTIME
  ModuleDependencyItem.DependencyScope.PROVIDED -> DependencyScope.PROVIDED
  ModuleDependencyItem.DependencyScope.TEST -> DependencyScope.TEST
}

private fun DependencyScope.toEntityDependencyScope(): ModuleDependencyItem.DependencyScope = when (this) {
  DependencyScope.COMPILE -> ModuleDependencyItem.DependencyScope.COMPILE
  DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
  DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
  DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
}

abstract class SdkOrderEntryBaseViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  item: ModuleDependencyItem,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntryViaTypedEntity(model, index, item, itemUpdater), LibraryOrSdkOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)
}

abstract class LibraryOrderEntryBaseViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  item: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryViaTypedEntity(model, index, item, itemUpdater), LibraryOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)

  override fun isValid(): Boolean = library != null
}

class LibraryOrderEntryViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  private val libraryDependencyItem: ModuleDependencyItem.Exportable.LibraryDependency,
  private val moduleLibraryTable: LibraryTable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : LibraryOrderEntryBaseViaTypedEntity(model, index, libraryDependencyItem, itemUpdater), LibraryOrderEntry, ClonableOrderEntry {

  override fun getPresentableName() = libraryName

  override val rootProvider: RootProvider?
    get() = library?.rootProvider

  override fun getLibraryLevel() = libraryDependencyItem.library.tableId.level

  override fun getLibraryName() = libraryDependencyItem.library.presentableName

  override fun getLibrary(): Library? {
    val libraryId = libraryDependencyItem.library

    val project = model.module.project

    val library = when (val parentId = libraryId.tableId) {
      is LibraryTableId.ProjectLibraryTableId -> {
        LibraryTablesRegistrar.getInstance()
          .getLibraryTableByLevel(LibraryTablesRegistrar.PROJECT_LEVEL, project)
          ?.getLibraryByName(libraryId.name)
      }
      is LibraryTableId.ModuleLibraryTableId ->
        moduleLibraryTable.libraries.firstOrNull { (it as LegacyBridgeLibrary).libraryId == libraryId }
      is LibraryTableId.GlobalLibraryTableId -> {
        LibraryTablesRegistrar.getInstance()
          ?.getLibraryTableByLevel(parentId.level, project)
          ?.getLibraryByName(libraryId.name)
      }
    }
/*
    TODO It's better to resolve libraries via id, review it again when it'll be possible

    val libraryId = libraryDependencyItem.library
    val libraryEntity = libraryId.resolve(model.storage) ?: return null

    val project = model.module.project

    val library = when (val libraryLevel = libraryEntity.table.level) {
      JpsLibraryTableSerializer.MODULE_LEVEL ->
        moduleLibraryTable.libraries.firstOrNull { (it as LegacyBridgeLibrary).libraryId == libraryId }
      JpsLibraryTableSerializer.PROJECT_LEVEL -> {
        val tableImpl = LibraryTablesRegistrar.getInstance()
          .getLibraryTableByLevel(libraryLevel, project) as LegacyBridgeProjectLibraryTableImpl
        tableImpl.findLibraryById(libraryId)
      }
      else -> LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, project)?.getLibraryByName(libraryEntity.name)
    }
*/
    return if (libraryId.tableId is LibraryTableId.ModuleLibraryTableId) {
      // model.accessor.getLibrary is not applicable to module libraries
      library
    } else {
      model.accessor.getLibrary(library, libraryName, libraryLevel)
    }
  }

  override fun isModuleLevel() = libraryLevel == JpsLibraryTableSerializer.MODULE_LEVEL

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitLibraryOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: RootModelImpl,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager): OrderEntry {
    TODO("not implemented")
  }
}

class SdkOrderEntryViaTypedEntity(
  model: RootModelViaTypedEntityImpl,
  index: Int,
  private val sdkDependencyItem: ModuleDependencyItem.SdkDependency
) : SdkOrderEntryBaseViaTypedEntity(model, index, sdkDependencyItem, null), ModuleJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getPresentableName() = "<${jdk?.name ?: sdkDependencyItem.sdkName}>"

  override fun getJdk(): Sdk? {
    val jdkTable = ProjectJdkTable.getInstance()

    val sdkType = sdkDependencyItem.sdkType
    val sdk = when {
      sdkType != null -> jdkTable.findJdk(sdkDependencyItem.sdkName, sdkType)
      else -> jdkTable.findJdk(sdkDependencyItem.sdkName)
    }
    return model.accessor.getSdk(sdk, sdkDependencyItem.sdkName)
  }

  override fun getJdkName() = sdkDependencyItem.sdkName

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: RootModelImpl,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager): OrderEntry {
    TODO("not implemented")
  }
}

class InheritedSdkOrderEntryViaTypedEntity(model: RootModelViaTypedEntityImpl, index: Int, item: ModuleDependencyItem.InheritedSdkDependency)
  : SdkOrderEntryBaseViaTypedEntity(model, index, item, null), InheritedJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getJdk(): Sdk? = model.accessor.getProjectSdk(model.module.project)
  override fun getJdkName(): String? = model.accessor.getProjectSdkName(model.module.project)

  override fun getPresentableName() = jdk?.let { "<${it.name}>" } ?: "<INVALID-INHERITED-JDK>"

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitInheritedJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: RootModelImpl,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager): OrderEntry {
    TODO("not implemented")
  }
}

class ModuleSourceOrderEntryViaTypedEntity(model: RootModelViaTypedEntityImpl, index: Int, item: ModuleDependencyItem.ModuleSourceDependency)
  : OrderEntryViaTypedEntity(model, index, item, null), ModuleSourceOrderEntry, ClonableOrderEntry {
  override fun getFiles(type: OrderRootType): Array<out VirtualFile> = if (type == OrderRootType.SOURCES) rootModel.sourceRoots else VirtualFile.EMPTY_ARRAY

  override fun getUrls(rootType: OrderRootType): Array<out String> = if (rootType == OrderRootType.SOURCES) rootModel.sourceRootUrls else ArrayUtil.EMPTY_STRING_ARRAY

  override fun getPresentableName(): String = ProjectBundle.message("project.root.module.source")

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? =
    policy.visitModuleSourceOrderEntry(this, initialValue)

  override fun getRootModel() = model

  override fun cloneEntry(rootModel: RootModelImpl,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager): OrderEntry {
    TODO("not implemented")
  }
}
