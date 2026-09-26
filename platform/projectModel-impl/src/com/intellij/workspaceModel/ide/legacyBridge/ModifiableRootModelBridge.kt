// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.SdkFinder
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModifiableRootModelBridge : ModifiableRootModel, ModificationTracker {
  fun prepareForCommit()
  fun postCommit()

  companion object {
    @ApiStatus.Obsolete(since = "2025.3")
    @JvmStatic
    fun findSdk(sdkName: String, sdkType: String): Sdk? = findSdk(sdkName, sdkType) { ProjectJdkTable.getInstance() }

    @JvmStatic
    fun findSdk(project: Project, sdkName: String, sdkType: String): Sdk? = findSdk(sdkName, sdkType) { ProjectJdkTable.getInstance(project) }

    private fun findSdk(sdkName: String, sdkType: String, projectJdkTableSupplier: () -> ProjectJdkTable): Sdk? {
      for (finder in SdkFinder.EP_NAME.extensionsIfPointIsRegistered) {
        val sdk = finder.findSdk(sdkName, sdkType)
        if (sdk != null) return sdk
      }
      return projectJdkTableSupplier().findJdk(sdkName, sdkType)
    }
  }
}