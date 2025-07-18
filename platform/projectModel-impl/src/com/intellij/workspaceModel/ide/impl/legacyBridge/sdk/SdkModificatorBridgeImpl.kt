// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.ELEMENT_ADDITIONAL
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import org.jdom.Element
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }

internal class SdkModificatorBridgeImpl(private val originalEntity: SdkEntity.Builder,
                               private val originalSdk: ProjectJdkImpl,
                               private val originalSdkDelegate: SdkBridgeImpl) : SdkModificator {

  private var isCommitted = false
  private var additionalData: SdkAdditionalData? = null
  private val modifiedSdkEntity: SdkEntity.Builder = SdkBridgeImpl.createEmptySdkEntity("", "", "")

  init {
    modifiedSdkEntity.applyChangesFrom(originalEntity)
    if (modifiedSdkEntity.additionalData.isNotEmpty()) {
      val additionalDataElement = JDOMUtil.load(modifiedSdkEntity.additionalData)
      additionalData = originalSdkDelegate.getSdkType().loadAdditionalData(originalSdkDelegate, additionalDataElement)
    }
  }

  internal fun setType(name: String) {
    modifiedSdkEntity.type = name
  }

  override fun getName(): String = modifiedSdkEntity.name

  override fun setName(name: String) {
    modifiedSdkEntity.name = name
  }

  override fun getHomePath(): String? = modifiedSdkEntity.homePath?.url

  override fun setHomePath(path: String?) {
    modifiedSdkEntity.homePath = if (path != null) {
      val descriptor = getMachine(path)
      val globalInstance = GlobalWorkspaceModel.getInstance(descriptor).getVirtualFileUrlManager()
      globalInstance.getOrCreateFromUrl(path)
    } else {
      null
    }
  }

  override fun getVersionString(): String? {
    return modifiedSdkEntity.version
  }

  override fun setVersionString(versionString: String?) {
    modifiedSdkEntity.version = versionString
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? {
    return additionalData
  }

  override fun setSdkAdditionalData(additionalData: SdkAdditionalData?) {
    this.additionalData =  additionalData
  }

  override fun getRoots(rootType: OrderRootType): Array<VirtualFile> {
    return modifiedSdkEntity.roots.filter { it.type.name == rootType.customName }
      .mapNotNull { it.url.virtualFile }
      .toTypedArray()
  }

  override fun addRoot(root: VirtualFile, rootType: OrderRootType) {
    val virtualFileUrlManager = GlobalWorkspaceModel.getInstance(LocalEelMachine).getVirtualFileUrlManager()
    modifiedSdkEntity.roots.add(
      SdkRoot(virtualFileUrlManager.getOrCreateFromUrl(root.url), rootTypes[rootType.customName]!!)
    )
  }

  override fun removeRoot(root: VirtualFile, rootType: OrderRootType) {
    val roots = modifiedSdkEntity.roots
    roots.removeIf { it.url.url == root.url && it.type.name == rootType.customName }
  }

  override fun removeRoots(rootType: OrderRootType) {
    val roots = modifiedSdkEntity.roots
    roots.removeIf { it.type.name == rootType.customName }
  }

  override fun removeAllRoots() {
    modifiedSdkEntity.roots = mutableListOf()
  }

  @RequiresWriteLock
  override fun commitChanges() {
    ThreadingAssertions.assertWriteAccess()
    if (isCommitted) error("Modification already completed")

    val descriptor = getMachine(modifiedSdkEntity.homePath?.toString())

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(descriptor)

    // In some cases we create SDK in air and need to modify it somehow e.g ProjectSdksModel.createSdkInternal,
    // so it's OK that entity may not be in storage

    modifiedSdkEntity.additionalData = if (additionalData != null) {
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      modifiedSdkEntity.getSdkType().saveAdditionalData(additionalData!!, additionalDataElement)
      JDOMUtil.write(additionalDataElement)
    } else ""

    // Update only entity existing in the storage
    val existingEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(originalSdk) as? SdkEntity
    existingEntity?.let { entity ->
      globalWorkspaceModel.updateModel("Modifying SDK ${SdkId(originalEntity.name, originalEntity.type)}") {
        it.modifySdkEntity(entity) {
          this.applyChangesFrom(modifiedSdkEntity)
        }
      }
    }
    originalSdkDelegate.applyChangesFrom(modifiedSdkEntity)

    if (existingEntity != null) {
      originalSdkDelegate.fireRootSetChanged()
    } else {
      // A workaround for the cases where or `ProjectJdkTableImpl` doesn't use, so the entities are in the air,
      // but any way we need to keep old contract and fire roots change event.
      // Example of such case: `ServerProjectJdkTable` with enabled new implementation, so we have Sdk(with entity in it),
      // but table stores SDKs in the list(not in the WSM).
      // Failed test: `com.jetbrains.python.PythonAnalysisToolSanityTest`
      if (ProjectJdkTable.getInstance().allJdks.toList().contains(originalSdk)) {
        originalSdkDelegate.fireRootSetChanged()
      }
    }
    isCommitted = true
  }

  override fun applyChangesWithoutWriteAction() {
    if (isCommitted) error("Modification already completed")

    modifiedSdkEntity.additionalData = if (additionalData != null) {
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      modifiedSdkEntity.getSdkType().saveAdditionalData(additionalData!!, additionalDataElement)
      JDOMUtil.write(additionalDataElement)
    } else ""

    originalSdkDelegate.applyChangesFrom(modifiedSdkEntity)
    isCommitted = true
  }

  override fun isWritable(): Boolean = !isCommitted

  override fun toString(): String {
    return "$name Version:$versionString Path:($homePath)"
  }

  private fun getMachine(path: String?): EelMachine {
    path ?: return LocalEelMachine
    return try {
      Path.of(path).getEelDescriptor().machine
    }
    catch (_: InvalidPathException) {
      // sometimes (in Ruby) the SDK home is set to 'temp:///root/nostubs'
      LocalEelMachine
    }
  }
}