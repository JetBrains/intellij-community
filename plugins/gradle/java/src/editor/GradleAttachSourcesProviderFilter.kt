// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.editor

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProviderFilter
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleAttachSourcesProviderFilter : AttachSourcesProviderFilter {

  override fun isApplicable(provider: AttachSourcesProvider, orderEntries: List<LibraryOrderEntry>, psiFile: PsiFile): Boolean {
    return orderEntries.mapNotNull { it.library?.externalSource?.id }
      .none { GradleConstants.SYSTEM_ID.id == it }
  }
}