// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import com.intellij.java.workspace.entities.javaSettings
import com.intellij.platform.workspace.jps.bridge.impl.JpsProjectBridge
import com.intellij.platform.workspace.jps.bridge.impl.JpsUrlListBridge
import com.intellij.platform.workspace.jps.bridge.impl.java.JpsJavaModuleExtensionBridge
import com.intellij.platform.workspace.jps.bridge.impl.java.JpsTestModulePropertiesBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.*
import org.jetbrains.jps.model.*
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase
import org.jetbrains.jps.model.impl.JpsExcludePatternImpl
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.library.sdk.JpsSdkReference
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.module.*
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer

internal class JpsModuleBridge(private val project: JpsProjectBridge,
                               val entity: ModuleEntity) 
  : JpsNamedCompositeElementBase<JpsModuleBridge>(entity.name), JpsTypedModule<JpsElement> {
  
  init {
    parent = project
  }  
    
  private val contentRoots by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsUrlListBridge(entity.contentRoots.map { it.url }, this)
  }
  private val excludeRoots by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsUrlListBridge(entity.contentRoots.flatMap { contentRootEntity -> contentRootEntity.excludedUrls.map { it.url } }, this)
  }
  private val excludePatternList by lazy(LazyThreadSafetyMode.PUBLICATION) {
    entity.contentRoots.flatMap { contentRootEntity ->
      contentRootEntity.excludedPatterns.map { pattern -> JpsExcludePatternImpl(contentRootEntity.url.url, pattern) } 
    }
  }
  private val sourceRootList by lazy(LazyThreadSafetyMode.PUBLICATION) {
    entity.sourceRoots.map { JpsModuleSourceRootBridge(it, this) }
  }
  private val dependenciesList by lazy(LazyThreadSafetyMode.PUBLICATION) { 
    JpsDependenciesListBridge(entity.dependencies, this) 
  }
  private val sdkReferencesTable by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val sdkId = entity.dependencies.filterIsInstance<SdkDependency>().firstNotNullOfOrNull { it.sdk }
    JpsSdkReferencesTableBridge(sdkId, this) 
  }
  private val moduleProperties by lazy(LazyThreadSafetyMode.PUBLICATION) {
    //todo store content of custom components from *.iml file in workspace model and use them here (IJPL-157852)
    getSerializer(entity.type?.name).loadProperties(null)
  }
  val javaModuleExtension: JpsJavaModuleExtensionBridge? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsJavaModuleExtensionBridge(entity.javaSettings, this)
  }
  val testModuleProperties: JpsTestModuleProperties? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    entity.testProperties?.let { JpsTestModulePropertiesBridge(it, this) }
  }

  override fun getContentRootsList(): JpsUrlList = contentRoots

  override fun getExcludeRootsList(): JpsUrlList = excludeRoots

  override fun getSourceRoots(): List<JpsModuleSourceRoot> = sourceRootList

  override fun <P : JpsElement?> getSourceRoots(type: JpsModuleSourceRootType<P>): Iterable<JpsTypedModuleSourceRoot<P>> {
    return sourceRootList.asSequence().filter { it.rootType == type }.filterIsInstance<JpsTypedModuleSourceRoot<P>>().asIterable()
  }

  override fun createReference(): JpsModuleReference = JpsModuleReferenceBridge(entity.name)

  override fun getExcludePatterns(): List<JpsExcludePattern> = excludePatternList

  override fun getDependenciesList(): JpsDependenciesList = dependenciesList

  override fun getLibraryCollection(): JpsLibraryCollection {
    return project.libraryBridgeCache.getLibraryCollection(LibraryTableId.ModuleLibraryTableId(entity.symbolicId), this)
  } 

  override fun getSdkReferencesTable(): JpsSdkReferencesTable = sdkReferencesTable

  override fun <P : JpsElement?> getSdkReference(type: JpsSdkType<P>): JpsSdkReference<P>? {
    return sdkReferencesTable.getSdkReference(type) ?: project.sdkReferencesTable.getSdkReference(type)
  }

  override fun <P : JpsElement?> getSdk(type: JpsSdkType<P>): JpsSdk<P>? {
    return getSdkReference(type)?.resolve()?.properties
  }

  override fun getProperties(): JpsElement = moduleProperties

  override fun getType(): JpsElementType<*> = moduleType

  override fun getModuleType(): JpsModuleType<JpsElement> {
    @Suppress("UNCHECKED_CAST")
    return getSerializer(entity.type?.name).type as JpsModuleType<JpsElement>
  }

  override fun getProject(): JpsProject = project

  override fun <P : JpsElement?> asTyped(type: JpsModuleType<P>): JpsTypedModule<P>? {
    @Suppress("UNCHECKED_CAST")
    return if (type == getType()) this as JpsTypedModule<P> else null
  }

  override fun delete() {
    reportModificationAttempt()
  }

  override fun addModuleLibrary(library: JpsLibrary) {
    reportModificationAttempt()
  }

  override fun addExcludePattern(baseDirUrl: String, pattern: String) {
    reportModificationAttempt()
  }

  override fun removeExcludePattern(baseDirUrl: String, pattern: String) {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?, Type> addModuleLibrary(name: String, type: Type & Any): JpsLibrary where Type : JpsLibraryType<P>?, Type : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?> addSourceRoot(url: String, rootType: JpsModuleSourceRootType<P>): JpsModuleSourceRoot {
    reportModificationAttempt()
  }

  override fun addSourceRoot(root: JpsModuleSourceRoot) {
    reportModificationAttempt()
  }

  override fun removeSourceRoot(url: String, rootType: JpsModuleSourceRootType<*>) {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?> addSourceRoot(url: String, rootType: JpsModuleSourceRootType<P>, properties: P & Any): JpsModuleSourceRoot {
    reportModificationAttempt()
  }

  override fun setName(name: String) {
    reportModificationAttempt()
  }
  
  companion object {
    val serializers by lazy { 
      JpsModelSerializerExtension.getExtensions().flatMap { it.modulePropertiesSerializers }.associateBy { it.typeId }
    }
    
    fun getSerializer(typeId: String?): JpsModulePropertiesSerializer<*> = serializers[typeId] ?: JpsProjectLoader.JAVA_MODULE_PROPERTIES_SERIALIZER 
  }
}