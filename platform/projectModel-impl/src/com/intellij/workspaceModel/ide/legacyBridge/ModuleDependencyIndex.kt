// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Maintains index of libraries and SDKs referenced from project's modules. This is an internal low-level API, it isn't supposed to be used
 * from plugins.
 */
@ApiStatus.Internal
interface ModuleDependencyIndex {
  companion object {
    fun getInstance(project: Project): ModuleDependencyIndex = project.service()
  }

  /**
   * Registers a listener to track dependencies on application-level libraries and libraries from custom application-level
   * tables. [ModuleDependencyListener.removedDependencyOn] methods are automatically called when the project is disposed. 
   */
  fun addListener(listener: ModuleDependencyListener)

  fun setupTrackedLibrariesAndJdks()

  /**
   * Return `true` if at least one module has dependency on 'Project SDK'
   */
  fun hasProjectSdkDependency(): Boolean
}

/**
 * The methods of this listener are called synchronously under 'write action' lock. 
 */
interface ModuleDependencyListener : EventListener {
  /** 
   * Called when [library] is added to dependency of some module, and there were no dependencies on this library before 
   */
  fun addedDependencyOn(library: Library)

  /**
   * Called when [library] is removed from dependencies of some module, and there are no dependencies on this library anymore 
   */
  fun removedDependencyOn(library: Library)

  /**
   * Called when [library] is created and some module has a dependency on this library (it was unresolved before) 
   */
  fun referencedLibraryAdded(library: Library)

  /**
   * Called when [library] is removed and some module has a dependency on this library (it will become unresolved)
   */
  fun referencedLibraryRemoved(library: Library)

  /**
   * Called when [sdk] is added to dependency of some module, and there were no dependencies on this SDK before
   */
  fun addedDependencyOn(sdk: Sdk)

  /**
   * Called when [sdk] is removed from dependencies of some module, and there are no dependencies on this SDK anymore
   */
  fun removedDependencyOn(sdk: Sdk)

  /**
   * Called when [sdk] is created and some module has a dependency on this SDK (it was unresolved before)
   */
  fun referencedSdkAdded(sdk: Sdk)

  /**
   * Called when [sdk] is removed and some module has a dependency on this SDK (it will become unresolved)
   */
  fun referencedSdkRemoved(sdk: Sdk)
}