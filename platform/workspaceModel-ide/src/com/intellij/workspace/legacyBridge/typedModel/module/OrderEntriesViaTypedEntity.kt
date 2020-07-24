package com.intellij.workspace.legacyBridge.typedModel.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ClonableOrderEntry
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import com.intellij.workspace.api.LibraryId
import com.intellij.workspace.api.LibraryTableId
import com.intellij.workspace.api.ModuleDependencyItem
import com.intellij.workspace.api.ModuleId
import com.intellij.workspace.legacyBridge.intellij.*
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal abstract class OrderEntryViaTypedEntity(
  private val rootModel: LegacyBridgeModuleRootModel,
  protected val index: Int,
  var item: ModuleDependencyItem,
  private val itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntry {
  protected val ownerLegacyModule: LegacyBridgeModule
    get() = rootModel.legacyBridgeModule

  protected val updater: ((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit
    get() = itemUpdater ?: error("This mode is read-only. Call from a modifiable model")

  fun getRootModel(): LegacyBridgeModuleRootModel = rootModel

  override fun getOwnerModule() = ownerLegacyModule
  override fun compareTo(other: OrderEntry?) = index.compareTo((other as OrderEntryViaTypedEntity).index)
  override fun isValid() = true
  override fun isSynthetic() = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OrderEntryViaTypedEntity

    if (ownerLegacyModule != other.ownerLegacyModule) return false
    if (index != other.index) return false
    if (item != other.item) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ownerLegacyModule.hashCode()
    result = 31 * result + index
    result = 31 * result + item.hashCode()
    return result
  }
}

internal abstract class ExportableOrderEntryViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  exportableDependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
): OrderEntryViaTypedEntity(rootModel, index, exportableDependencyItem, itemUpdater), ExportableOrderEntry {

  private var exportedVar = exportableDependencyItem.exported
  private var scopeVar = exportableDependencyItem.scope

  override fun isExported() = exportedVar
  override fun setExported(value: Boolean) {
    if (exportedVar == value) return
    updater { (it as ModuleDependencyItem.Exportable).withExported(value) }
    exportedVar = value
    item = (item as ModuleDependencyItem.Exportable).withExported(value)
  }

  override fun getScope() = scopeVar.toDependencyScope()
  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater { (it as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope()) }
    scopeVar = scope.toEntityDependencyScope()
    item = (item as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope())
  }
}

internal abstract class ModuleOrderEntryBaseViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  dependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryViaTypedEntity(rootModel, index, dependencyItem, itemUpdater), ModuleOrderEntry, ClonableOrderEntry {

  override fun getFiles(type: OrderRootType): Array<VirtualFile> {
    return getEnumerator(type)?.roots ?: VirtualFile.EMPTY_ARRAY
  }

  override fun getUrls(rootType: OrderRootType): Array<String> {
    return getEnumerator(rootType)?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  private fun getEnumerator(rootType: OrderRootType) = ownerLegacyModule.let { ModuleRootManagerImpl.getCachingEnumeratorForType(rootType, it) }

  override fun getPresentableName() = moduleName
  override fun isValid(): Boolean = module != null

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitModuleOrderEntry(this, initialValue)
}

internal class ModuleOrderEntryViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  private val moduleDependencyItem: ModuleDependencyItem.Exportable.ModuleDependency,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ModuleOrderEntryBaseViaTypedEntity(rootModel, index, moduleDependencyItem, itemUpdater) {

  private var productionOnTestVar = moduleDependencyItem.productionOnTest

  override fun getModule(): Module? {
    // TODO It's better to resolve modules via id when it'll be possible
    val moduleManager = ModuleManager.getInstance(ownerLegacyModule.project) as LegacyBridgeModuleManagerComponent
    val module = moduleManager.findModuleByName(moduleName) ?: {
      getRootModel().storage.resolve(moduleDependencyItem.module)?.let {
        moduleManager.findUncommittedModuleByName(it.name)
      }
    }.invoke()
    return getRootModel().accessor.getModule(module, moduleName)
  }

  override fun getModuleName() = moduleDependencyItem.module.name

  override fun isProductionOnTestDependency() = productionOnTestVar

  override fun setProductionOnTestDependency(productionOnTestDependency: Boolean) {
    if (productionOnTestVar == productionOnTestDependency) return
    updater { item -> (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency) }
    productionOnTestVar = productionOnTestDependency
    item = (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency)
  }

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleOrderEntryViaTypedEntity(
    rootModel as LegacyBridgeModuleRootModel,
    index, moduleDependencyItem.copy(), null
  )
}

fun ModuleDependencyItem.DependencyScope.toDependencyScope() = when (this) {
  ModuleDependencyItem.DependencyScope.COMPILE -> DependencyScope.COMPILE
  ModuleDependencyItem.DependencyScope.RUNTIME -> DependencyScope.RUNTIME
  ModuleDependencyItem.DependencyScope.PROVIDED -> DependencyScope.PROVIDED
  ModuleDependencyItem.DependencyScope.TEST -> DependencyScope.TEST
}

fun DependencyScope.toEntityDependencyScope(): ModuleDependencyItem.DependencyScope = when (this) {
  DependencyScope.COMPILE -> ModuleDependencyItem.DependencyScope.COMPILE
  DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
  DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
  DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
}

internal abstract class SdkOrderEntryBaseViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  item: ModuleDependencyItem,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntryViaTypedEntity(rootModel, index, item, itemUpdater), LibraryOrSdkOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)
}

internal abstract class LibraryOrderEntryBaseViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  item: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryViaTypedEntity(rootModel, index, item, itemUpdater), LibraryOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)

  override fun isValid(): Boolean = library != null
}

internal class LibraryOrderEntryViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  internal val libraryDependencyItem: ModuleDependencyItem.Exportable.LibraryDependency,
  private val moduleLibrary: Library?,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : LibraryOrderEntryBaseViaTypedEntity(rootModel, index, libraryDependencyItem, itemUpdater), LibraryOrderEntry, ClonableOrderEntry {

  override fun getPresentableName(): String = libraryName ?: getPresentableNameForUnnamedLibrary()

  private fun getPresentableNameForUnnamedLibrary(): String {
    val url = getUrls(OrderRootType.CLASSES).firstOrNull()
    return if (url != null) PathUtil.toPresentableUrl(url) else ProjectModelBundle.message("library.empty.library.item")
  }

  override val rootProvider: RootProvider?
    get() = library?.rootProvider

  override fun getLibraryLevel() = libraryDependencyItem.library.tableId.level

  override fun getLibraryName() = LegacyBridgeLibraryImpl.getLegacyLibraryName(libraryDependencyItem.library)

  override fun getLibrary(): Library? {
    val libraryId = libraryDependencyItem.library

    val project = ownerLegacyModule.project

    val library = when (val parentId = libraryId.tableId) {
      is LibraryTableId.ProjectLibraryTableId -> {
        LibraryTablesRegistrar.getInstance()
          .getLibraryTableByLevel(LibraryTablesRegistrar.PROJECT_LEVEL, project)
          ?.getLibraryByName(libraryId.name)
      }
      is LibraryTableId.ModuleLibraryTableId -> moduleLibrary
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
      getRootModel().accessor.getLibrary(library, libraryName, libraryLevel)
    }
  }

  override fun isModuleLevel() = libraryLevel == JpsLibraryTableSerializer.MODULE_LEVEL

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitLibraryOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry {
    val libraryTableId = if (libraryLevel == JpsLibraryTableSerializer.MODULE_LEVEL) {
      LibraryTableId.ModuleLibraryTableId(ModuleId(rootModel.module.name))
    }
    else toLibraryTableId(libraryLevel)

    val libraryId = LibraryId(libraryDependencyItem.library.name, libraryTableId)
    val libraryDependencyItemCopy = libraryDependencyItem.copy(library = libraryId)

    val moduleLibraryCopy = moduleLibrary?.let {
      val libraryTable = rootModel.moduleLibraryTable as LegacyBridgeModifiableModuleLibraryTable
      libraryTable.createLibraryCopy(it as LegacyBridgeLibraryImpl)
    }
    return LibraryOrderEntryViaTypedEntity(rootModel as LegacyBridgeModuleRootModel, index, libraryDependencyItemCopy, moduleLibraryCopy, null)
  }

  override fun isSynthetic(): Boolean = isModuleLevel

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LibraryOrderEntryViaTypedEntity) return false
    if (!super.equals(other)) return false

    if (moduleLibrary != other.moduleLibrary) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + (moduleLibrary?.hashCode() ?: 0)
    return result
  }
}

internal class SdkOrderEntryViaTypedEntity(
  rootModel: LegacyBridgeModuleRootModel,
  index: Int,
  internal val sdkDependencyItem: ModuleDependencyItem.SdkDependency
) : SdkOrderEntryBaseViaTypedEntity(rootModel, index, sdkDependencyItem, null), ModuleJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getPresentableName() = "< ${jdk?.name ?: sdkDependencyItem.sdkName} >"

  override fun isValid(): Boolean = jdk != null

  override fun getJdk(): Sdk? {
    val jdkTable = ProjectJdkTable.getInstance()

    val sdkType = sdkDependencyItem.sdkType
    val sdk = jdkTable.findJdk(sdkDependencyItem.sdkName, sdkType)
    return getRootModel().accessor.getSdk(sdk, sdkDependencyItem.sdkName)
  }

  override fun getJdkName() = sdkDependencyItem.sdkName

  override fun getJdkTypeName() = sdkDependencyItem.sdkType

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = SdkOrderEntryViaTypedEntity(rootModel as LegacyBridgeModuleRootModel, index, sdkDependencyItem.copy())

  override fun isSynthetic(): Boolean = true
}

internal class InheritedSdkOrderEntryViaTypedEntity(rootModel: LegacyBridgeModuleRootModel, index: Int, item: ModuleDependencyItem.InheritedSdkDependency)
  : SdkOrderEntryBaseViaTypedEntity(rootModel, index, item, null), InheritedJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getJdk(): Sdk? = getRootModel().accessor.getProjectSdk(getRootModel().legacyBridgeModule.project)
  override fun getJdkName(): String? = getRootModel().accessor.getProjectSdkName(getRootModel().legacyBridgeModule.project)

  override fun isValid(): Boolean = jdk != null

  override fun getPresentableName() = "< $jdkName >"

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitInheritedJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = InheritedSdkOrderEntryViaTypedEntity(
    rootModel as LegacyBridgeModuleRootModel, index, ModuleDependencyItem.InheritedSdkDependency
  )
}

internal class ModuleSourceOrderEntryViaTypedEntity(rootModel: LegacyBridgeModuleRootModel, index: Int, item: ModuleDependencyItem.ModuleSourceDependency)
  : OrderEntryViaTypedEntity(rootModel, index, item, null), ModuleSourceOrderEntry, ClonableOrderEntry {
  override fun getFiles(type: OrderRootType): Array<out VirtualFile> = if (type == OrderRootType.SOURCES) rootModel.sourceRoots else VirtualFile.EMPTY_ARRAY

  override fun getUrls(rootType: OrderRootType): Array<out String> = if (rootType == OrderRootType.SOURCES) rootModel.sourceRootUrls else ArrayUtil.EMPTY_STRING_ARRAY

  override fun getPresentableName(): String = ProjectModelBundle.message("project.root.module.source")

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? =
    policy.visitModuleSourceOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleSourceOrderEntryViaTypedEntity(
    rootModel as LegacyBridgeModuleRootModel, index, ModuleDependencyItem.ModuleSourceDependency
  )

  override fun isSynthetic(): Boolean = true
}
