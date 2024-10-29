// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryCollectionsCache
import com.intellij.platform.workspace.jps.bridge.impl.module.*
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsLibraryDependencyBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsSdkReferencesTableBridge
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties
import org.jetbrains.jps.model.impl.JpsProjectBase
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension
import org.jetbrains.jps.model.java.JpsJavaModuleExtension
import org.jetbrains.jps.model.java.impl.JpsJavaAwareProject
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.module.*

internal class JpsProjectBridge(modelBridge: JpsModelBridge,
                                entityStorage: EntityStorage,
                                val additionalData: JpsProjectAdditionalData) 
  : JpsProjectBase(modelBridge), JpsJavaAwareProject {
    
  internal val libraryBridgeCache by lazy(LazyThreadSafetyMode.PUBLICATION) { JpsLibraryCollectionsCache(entityStorage) }
  private val modules by lazy(LazyThreadSafetyMode.PUBLICATION) { 
    entityStorage.entities(ModuleEntity::class.java).sortedBy { it.name }.mapTo(ArrayList()) { 
      JpsModuleBridge(this, it)
    } 
  }
  private val sdkReferencesTable = JpsSdkReferencesTableBridge(additionalData.projectSdkId, this)

  override fun getModules(): List<JpsModuleBridge> = modules

  override fun <P : JpsElement?> getModules(type: JpsModuleType<P>): Iterable<JpsTypedModule<P>> {
    return modules.asSequence()
      .filter { it.moduleType == type }
      .filterIsInstance<JpsTypedModule<P>>()
      .asIterable()
  }

  override fun getLibraryCollection(): JpsLibraryCollection {
    return libraryBridgeCache.getLibraryCollection(LibraryTableId.ProjectLibraryTableId, this)
  }

  override fun getSdkReferencesTable(): JpsSdkReferencesTable = sdkReferencesTable

  override fun getName(): String = additionalData.projectName

  override fun getJavaModuleExtension(module: JpsModule): JpsJavaModuleExtension? {
    return (module as? JpsModuleBridge)?.javaModuleExtension
  }

  override fun getJavaDependencyExtension(element: JpsDependencyElement): JpsJavaDependencyExtension? {
    return when (element) {
      is JpsLibraryDependencyBridge -> element.javaExtension
      is JpsModuleDependencyBridge -> element.javaExtension
      else -> null
    }
  }

  override fun getTestModuleProperties(module: JpsModule): JpsTestModuleProperties? {
    return (module as? JpsModuleBridge)?.testModuleProperties
  }

  override fun isProductionOnTestDependency(element: JpsDependencyElement): Boolean {
    return element is JpsModuleDependencyBridge && element.productionOnTest
  }

  override fun <P : JpsElement?, ModuleType> addModule(name: String, moduleType: ModuleType & Any): JpsModule where ModuleType : JpsModuleType<P>?, ModuleType : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun addModule(module: JpsModule) {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?, LibraryType> addLibrary(name: String, libraryType: LibraryType & Any): JpsLibrary where LibraryType : JpsLibraryType<P>?, LibraryType : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun setName(name: String) {
    reportModificationAttempt()
  }

  override fun removeModule(module: JpsModule) {
    reportModificationAttempt()
  }
}