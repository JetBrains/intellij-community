// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.jvm.JvmMetaLanguage
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiErrorElement
import org.jetbrains.idea.devkit.util.PsiUtil.isPluginModule

internal class TestFragmentsErrorFilter : HighlightErrorFilter() {
  override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
    val project = element.project

    val injectionHost = InjectedLanguageManager.getInstance(project).getInjectionHost(element) ?: return true
    val module = ModuleUtilCore.findModuleForPsiElement(injectionHost) ?: return true

    val containingFile = injectionHost.containingFile
    val virtualFile = containingFile.virtualFile ?: return true
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)
        && !isPluginModule(module)) {
      return true
    }

    if (!module.moduleTestSourceScope.contains(virtualFile)) return true  // let's consider it as test data

    return !Language.findInstance(JvmMetaLanguage::class.java).matchesLanguage(containingFile.language) // only inside JVM-language files
  }
}
