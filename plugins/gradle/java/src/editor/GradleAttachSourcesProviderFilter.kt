// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.editor

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProviderFilter
import com.intellij.jarFinder.InternetAttachSourceProvider
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiFile
import com.intellij.workspaceModel.ide.toExternalSource
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleAttachSourcesProviderFilter : AttachSourcesProviderFilter {

  override fun isApplicable(provider: AttachSourcesProvider, libraries: Collection<LibraryEntity>, psiFile: PsiFile): Boolean {

    return if (libraries.mapNotNull { (it.entitySource as? JpsImportedEntitySource)?.toExternalSource()?.id }.any { GradleConstants.SYSTEM_ID.id == it }) {
      provider !is InternetAttachSourceProvider
    } else {
      true
    }
  }
}