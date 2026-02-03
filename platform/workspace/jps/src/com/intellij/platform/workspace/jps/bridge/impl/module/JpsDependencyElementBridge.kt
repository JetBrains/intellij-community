// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import com.intellij.platform.workspace.jps.bridge.impl.java.JpsJavaDependencyExtensionBridge
import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryReferenceBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import org.jetbrains.jps.model.ex.JpsCompositeElementBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.library.sdk.JpsSdkReference
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.module.*

internal sealed class JpsDependencyElementBridge(parentElement: JpsDependenciesListBridge) 
  : JpsCompositeElementBase<JpsDependencyElementBridge>(), JpsDependencyElement {
  
  init {
    parent = parentElement 
  }  
    
  override fun getContainingModule(): JpsModule = (parent as JpsDependenciesListBridge).parent as JpsModuleBridge

  override fun remove() {
    reportModificationAttempt()
  }
}

internal class JpsModuleDependencyBridge(private val dependency: ModuleDependency, parentElement: JpsDependenciesListBridge) 
  : JpsDependencyElementBridge(parentElement), JpsModuleDependency {

  private val moduleReference = JpsModuleReferenceBridge(dependency.module.name).also { it.parent = this } 
  private val resolved by lazy(LazyThreadSafetyMode.PUBLICATION) { moduleReference.resolve() }
  val javaExtension by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsJavaDependencyExtensionBridge(dependency.exported, dependency.scope, this)
  }
  val productionOnTest: Boolean
    get() = dependency.productionOnTest
    
  override fun getModuleReference(): JpsModuleReference = moduleReference

  override fun getModule(): JpsModule? = resolved
}

internal class JpsLibraryDependencyBridge(private val dependency: LibraryDependency, parentElement: JpsDependenciesListBridge)
  : JpsDependencyElementBridge(parentElement), JpsLibraryDependency {
    
  private val libraryReference = JpsLibraryReferenceBridge(dependency.library).also { it.parent = this }
  private val resolved by lazy(LazyThreadSafetyMode.PUBLICATION) { libraryReference.resolve() }
  val javaExtension by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsJavaDependencyExtensionBridge(dependency.exported, dependency.scope, this)
  }

  override fun getLibraryReference(): JpsLibraryReference = libraryReference

  override fun getLibrary(): JpsLibrary? = resolved
}

internal class JpsModuleSourceDependencyBridge(parentElement: JpsDependenciesListBridge) 
  : JpsDependencyElementBridge(parentElement), JpsModuleSourceDependency

internal class JpsSdkDependencyBridge(private val sdkType: JpsSdkType<*>, parentElement: JpsDependenciesListBridge) 
  : JpsDependencyElementBridge(parentElement), JpsSdkDependency {
    
  override fun getSdkType(): JpsSdkType<*> = sdkType

  override fun resolveSdk(): JpsLibrary? {
    return sdkReference?.resolve()
  }

  override fun getSdkReference(): JpsSdkReference<*>? = containingModule.getSdkReference(sdkType)
}