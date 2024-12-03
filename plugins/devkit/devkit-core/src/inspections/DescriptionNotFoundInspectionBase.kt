// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.idea.devkit.inspections.ExtensionUtil.isExtensionPointImplementationCandidate
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix

internal abstract class DescriptionNotFoundInspectionBase(private val descriptionType: DescriptionType) :
  DevKitJvmInspection.ForClass() {

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (!isExtensionPointImplementationCandidate(psiClass)) return
    if (!descriptionType.matches(psiClass)) return

    val module = ModuleUtilCore.findModuleForPsiElement(psiClass)?: return

    val descriptionTypeResolver = descriptionType.createDescriptionTypeResolver(module, psiClass)
    if (descriptionTypeResolver.skipIfNotRegisteredInPluginXml()) return

    if (descriptionTypeResolver.resolveDescriptionFile() == null) {
      sink.highlight(getHasNotDescriptionError(module, psiClass),
                     CreateHtmlDescriptionFix(descriptionTypeResolver.getDescriptionDirName(), module, descriptionType))
      return
    }

    if (!descriptionType.hasBeforeAfterTemplateFiles()) return

    if (descriptionTypeResolver.resolveBeforeAfterTemplateFiles().size != 2 &&
        !descriptionTypeResolver.skipOptionalBeforeAfterTemplateFiles()) {
      sink.highlight(getHasNotBeforeAfterError())
    }
  }

  protected abstract fun getHasNotDescriptionError(module: Module?, psiClass: PsiClass?): @InspectionMessage String

  protected abstract fun getHasNotBeforeAfterError(): @InspectionMessage String
}