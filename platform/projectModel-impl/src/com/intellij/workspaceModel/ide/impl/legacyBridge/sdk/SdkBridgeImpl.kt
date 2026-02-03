// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkBridge
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.PersistentOrderRootType
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkEntityBuilder
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.serialization.impl.JpsGlobalEntitiesSerializers
import com.intellij.platform.workspace.jps.serialization.impl.JpsSdkEntitySerializer
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetImpl
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.util.JpsPathUtil
import java.util.function.Function


// SdkBridgeImpl.clone called from com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.reset
// So I need to have such implementation and can't use implementation with SymbolicId and searching in storage
@ApiStatus.Internal
class SdkBridgeImpl(
  private var sdkEntityBuilder: SdkEntityBuilder,
  private var environmentName: InternalEnvironmentName,
) : UserDataHolderBase(), SdkBridge, RootProvider, Sdk {

  private var additionalData: SdkAdditionalData? = null
  private val dispatcher = EventDispatcher.create(RootSetChangedListener::class.java)

  init {
    reloadAdditionalData()
  }

  override fun getSdkType(): SdkTypeId = sdkEntityBuilder.getSdkType()

  override fun getName(): String = sdkEntityBuilder.name

  override fun getVersionString(): String? = sdkEntityBuilder.version

  override fun getHomePath(): String? = JpsPathUtil.urlToPath(sdkEntityBuilder.homePath?.url)

  override fun getHomeDirectory(): VirtualFile? {
    val homePath = getHomePath()
    if (homePath == null) return null
    return StandardFileSystems.local().findFileByPath(homePath)
  }

  override fun getSdkModificator(): SdkModificator {
    // It doesn't expect to be called directly, all calls are proxied from [ProjectJdkIml], so no direct calls.
    throw UnsupportedOperationException()
  }

  fun getSdkModificator(originSdk: ProjectJdkImpl): SdkModificator {
    return SdkModificatorBridgeImpl(sdkEntityBuilder, originSdk, this, environmentName)
  }

  override fun getRootProvider(): RootProvider = this

  override fun getUrls(rootType: OrderRootType): Array<String> {
    val customName = rootType.customName
    return sdkEntityBuilder.roots.filter { it.type.name == customName }
      .map { it.url.url }
      .toTypedArray()
  }

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    val customName = rootType.customName
    val roots = sdkEntityBuilder.roots
    val result = ArrayList<VirtualFile>(roots.size)
    for (root in roots) {
      if (root.type.name == customName) {
        root.url.virtualFile?.let { result += it }
      }
    }
    return result.toTypedArray()
  }


  override fun addRootSetChangedListener(listener: RootSetChangedListener) {
    dispatcher.addListener(listener)
  }

  override fun removeRootSetChangedListener(listener: RootSetChangedListener) {
    dispatcher.removeListener(listener)
  }

  override fun addRootSetChangedListener(listener: RootSetChangedListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  fun fireRootSetChanged() {
    if (dispatcher.hasListeners()) {
      dispatcher.multicaster.rootSetChanged(this)
    }
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? = additionalData

  override fun clone(): SdkBridgeImpl {
    val sdkEntityClone = createEmptySdkEntity("", "", environmentName = environmentName)
    sdkEntityClone.applyChangesFrom(sdkEntityBuilder)
    return SdkBridgeImpl(sdkEntityClone, environmentName)
  }

  @ApiStatus.Internal
  override fun changeType(newType: SdkTypeId, additionalDataElement: Element?) {
    ThreadingAssertions.assertWriteAccess()
    sdkModificator.let {
      it as SdkModificatorBridgeImpl
      it.setType(newType.name)
      it.sdkAdditionalData = if (additionalDataElement != null) newType.loadAdditionalData(this, additionalDataElement) else null
      it.commitChanges()
    }
  }

  override fun readExternal(element: Element) {
    val sdkSerializer = createSerializer(InternalEnvironmentName.Local)
    val sdkEntity = sdkSerializer.loadSdkEntity(element, getVirtualFileUrlManager())

    sdkEntityBuilder.applyChangesFrom(sdkEntity)
    reloadAdditionalData()
  }

  override fun readExternal(element: Element, sdkTypeByNameFunction: Function<String, SdkTypeId>) {
    throw UnsupportedOperationException()
  }

  override fun writeExternal(element: Element) {
    createSerializer(environmentName).saveSdkEntity(element, sdkEntityBuilder)
  }

  private fun createSerializer(environmentName: InternalEnvironmentName): JpsSdkEntitySerializer {
    val sortedRootTypes = OrderRootType.getSortedRootTypes().mapNotNull { it.sdkRootName }
    return JpsGlobalEntitiesSerializers.createSdkSerializer(getVirtualFileUrlManager(), sortedRootTypes,
                                                            PathManager.getOptionsDir(), environmentName)
  }

  fun getRawSdkAdditionalData(): String = sdkEntityBuilder.additionalData

  fun applyChangesFrom(sdkBridge: SdkBridgeImpl) {
    applyChangesFrom(sdkBridge.sdkEntityBuilder)
  }

  internal fun applyChangesFrom(sdkEntity: SdkEntityBuilder) {
    val modifiableEntity = sdkEntityBuilder as ModifiableWorkspaceEntityBase<*, *>
    if (modifiableEntity.diff != null && !modifiableEntity.modifiable.get()) {
      sdkEntityBuilder = createEmptySdkEntity("", "", "", environmentName = environmentName)
    }
    sdkEntityBuilder.applyChangesFrom(sdkEntity)
    reloadAdditionalData()
  }

  internal fun reloadAdditionalData() {
    val rawAdditionalData = sdkEntityBuilder.additionalData
    if (rawAdditionalData.isNotBlank()) {
      val additionalDataElement = JDOMUtil.load(rawAdditionalData)
      additionalData = sdkEntityBuilder.getSdkType().loadAdditionalData(this, additionalDataElement)
    }
  }

  internal fun applyChangesTo(sdkEntity: SdkEntityBuilder) {
    sdkEntity.applyChangesFrom(sdkEntityBuilder)
  }

  fun getEntityBuilder(): SdkEntityBuilder = sdkEntityBuilder

  override fun toString(): String {
    return "$name Version:$versionString Path:($homePath)"
  }

  companion object {
    private val SDK_BRIDGE_MAPPING_ID = ExternalMappingKey.create<Sdk>("intellij.sdk.bridge")

    val EntityStorage.sdkMap: ExternalEntityMapping<Sdk>
      get() = getExternalMapping(SDK_BRIDGE_MAPPING_ID)

    val MutableEntityStorage.mutableSdkMap: MutableExternalEntityMapping<Sdk>
      get() = getMutableExternalMapping(SDK_BRIDGE_MAPPING_ID)

    fun EntityStorage.findSdkEntity(sdk: Sdk): SdkEntity? =
      sdkMap.getEntities(sdk).firstOrNull() as SdkEntity?

    fun EntityStorage.findSdk(sdkEntity: SdkEntity): Sdk? =
      sdkMap.getDataByEntity(sdkEntity)

    /** @return a [Sdk] which contains [set] or null otherwise */
    fun ImmutableEntityStorage.findSdk(set: WorkspaceFileSet): Sdk? {
      val setImpl = set as? WorkspaceFileSetImpl ?: return null
      val sdkEntity = setImpl.entityPointer.resolve(this) as? SdkEntity ?: return null
      return findSdk(sdkEntity)
    }

    fun createEmptySdkEntity(
      name: String,
      type: String,
      homePath: String = "",
      version: String? = null,
      environmentName: InternalEnvironmentName,
    ): SdkEntityBuilder {
      val sdkEntitySource = createEntitySourceForSdk(environmentName)
      val virtualFileUrlManager = getVirtualFileUrlManager()
      val homePathVfu = virtualFileUrlManager.getOrCreateFromUrl(homePath)
      return SdkEntity(name, type, emptyList(), "", sdkEntitySource) {
        this.homePath = homePathVfu
        this.version = version
      } as SdkEntityBuilder
    }

    fun createEntitySourceForSdk(environmentName: InternalEnvironmentName): EntitySource {
      val virtualFileUrlManager = getVirtualFileUrlManager()
      val optionsDir = PathManager.getOptionsDir()
      val environmentDir = when (environmentName) {
        InternalEnvironmentName.Local -> optionsDir
        is InternalEnvironmentName.Custom -> optionsDir.resolve(environmentName.name)
      }
      val sdkFile = environmentDir.resolve(JpsGlobalEntitiesSerializers.SDK_FILE_NAME + PathManager.DEFAULT_EXT)
      return JpsGlobalFileEntitySource(virtualFileUrlManager.getOrCreateFromUrl(sdkFile.toAbsolutePath().toString()))
    }
  }
}

internal fun SdkEntityBuilder.getSdkType(): SdkTypeId {
  return ProjectJdkTable.getInstance().getSdkTypeByName(type)
}

// At serialization, we have access only to `sdkRootName` so our roots contains only this names
// that's why we need to associate such names with the actual root type
@get:ApiStatus.Internal
val OrderRootType.customName: String
  get() {
    if (this is PersistentOrderRootType) {
      // Only `NativeLibraryOrderRootType` don't have rootName all other elements with it
      return sdkRootName ?: name()
    }
    else {
      // It's only for `DocumentationRootType` this is the only class that doesn't extend `PersistentOrderRootType`
      return name()
    }
  }

@ApiStatus.Internal
fun SdkEntityBuilder.applyChangesFrom(fromSdk: SdkEntityBuilder) {
  name = fromSdk.name
  type = fromSdk.type
  version = fromSdk.version
  homePath = fromSdk.homePath
  val sdkRoots = fromSdk.roots.mapTo(mutableListOf()) { SdkRoot(it.url, it.type) }
  roots = sdkRoots
  additionalData = fromSdk.additionalData
  entitySource = fromSdk.entitySource
}

@ApiStatus.Internal
fun SdkEntityBuilder.applyChangesFrom(fromSdk: SdkEntity) {
  name = fromSdk.name
  type = fromSdk.type
  version = fromSdk.version
  homePath = fromSdk.homePath
  val sdkRoots = fromSdk.roots.mapTo(mutableListOf()) { SdkRoot(it.url, it.type) }
  roots = sdkRoots
  additionalData = fromSdk.additionalData
  entitySource = fromSdk.entitySource
}

private fun getVirtualFileUrlManager(): VirtualFileUrlManager {
  // here we can use LocalEelMachine, as we simply need to get the virtual file url manager
  return GlobalWorkspaceModel.getInstance(LocalEelMachine).getVirtualFileUrlManager()
}
