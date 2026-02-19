// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit.message

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

internal class CommitMessageInspectionEP: CustomLoadingExtensionPointBean<BaseCommitMessageInspection>() {

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null

  override fun getImplementationClassName(): String? = implementation

  companion object {

    @ApiStatus.Experimental
    @JvmField
    val EP_NAME: ExtensionPointName<CommitMessageInspectionEP> = ExtensionPointName<CommitMessageInspectionEP>("com.intellij.vcs.commitMessageInspection")

  }

}