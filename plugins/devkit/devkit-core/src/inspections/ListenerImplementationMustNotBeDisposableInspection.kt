// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils

internal class ListenerImplementationMustNotBeDisposableInspection : DevKitJvmInspection.ForClass() {

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) return

    if (!InheritanceUtil.isInheritor(psiClass, Disposable::class.java.canonicalName)) return

    if (!isRegisteredListener(project, psiClass)) return

    sink.highlight(DevKitBundle.message("inspections.listener.implementation.must.not.implement.disposable"))
  }

  private fun isRegisteredListener(project: Project, psiClass: PsiClass): Boolean {
    return !IdeaPluginRegistrationIndex.processListener(
      project,
      psiClass,
      PluginRelatedLocatorsUtils.getCandidatesScope(project),
    ) { false }
  }

}
