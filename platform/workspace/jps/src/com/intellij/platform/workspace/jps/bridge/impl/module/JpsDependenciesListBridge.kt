// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import com.intellij.platform.workspace.jps.bridge.impl.JpsProjectBridge
import com.intellij.platform.workspace.jps.bridge.impl.library.sdk.JpsSdkBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.*
import org.jetbrains.jps.model.ex.JpsCompositeElementBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.module.*

internal class JpsDependenciesListBridge(dependencyItems: List<ModuleDependencyItem>, parentElement: JpsModuleBridge) 
  : JpsCompositeElementBase<JpsDependenciesListBridge>(), JpsDependenciesList {
  
  private val dependencies: List<JpsDependencyElementBridge> 
  init {
    parent = parentElement
    dependencies = dependencyItems.map { item ->
      when (item) {
        is ModuleDependency -> JpsModuleDependencyBridge(item, this)
        is LibraryDependency -> JpsLibraryDependencyBridge(item, this)
        ModuleSourceDependency -> JpsModuleSourceDependencyBridge(this)
        InheritedSdkDependency -> {
          val projectJdkTypeId = (model?.project as? JpsProjectBridge)?.additionalData?.projectSdkId?.type
          val projectJdkType = JpsSdkBridge.getSerializer(projectJdkTypeId).type
          JpsSdkDependencyBridge(projectJdkType, this)
        }
        is SdkDependency -> JpsSdkDependencyBridge(JpsSdkBridge.getSerializer(item.sdk.type).type, this)
      } 
    }
  }

  override fun getDependencies(): List<JpsDependencyElement> = dependencies

  override fun addModuleDependency(module: JpsModule): JpsModuleDependency {
    reportModificationAttempt()
  }

  override fun addModuleDependency(moduleReference: JpsModuleReference): JpsModuleDependency {
    reportModificationAttempt()
  }

  override fun addLibraryDependency(libraryElement: JpsLibrary): JpsLibraryDependency {
    reportModificationAttempt()
  }

  override fun addLibraryDependency(libraryReference: JpsLibraryReference): JpsLibraryDependency {
    reportModificationAttempt()
  }

  override fun addModuleSourceDependency() {
    reportModificationAttempt()
  }

  override fun addSdkDependency(sdkType: JpsSdkType<*>) {
    reportModificationAttempt()
  }

  override fun clear() {
    reportModificationAttempt()
  }
}
