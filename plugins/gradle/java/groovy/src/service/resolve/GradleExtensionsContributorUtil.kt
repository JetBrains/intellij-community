// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.service.resolve

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import icons.GradleIcons
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase
import javax.swing.Icon

class GradleExtensionsContributorUtil {
  companion object {
    const val PROPERTIES_FILE_ORIGINAL_INFO : String = "by gradle.properties"

    fun getExtensionsFor(psiElement: PsiElement): GradleExtensionsSettings.GradleExtensionsData? {
      val project = psiElement.project
      val virtualFile = psiElement.containingFile?.originalFile?.virtualFile ?: return null
      val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
      return GradleExtensionsSettings.getInstance(project).getExtensionsFor(module)
    }

    class StaticVersionCatalogProperty(place: PsiElement, name: String, val clazz: PsiClass) : GroovyPropertyBase(name, place) {
      override fun getPropertyType(): PsiType {
        return PsiElementFactory.getInstance(project).createType(clazz, PsiSubstitutor.EMPTY)
      }

      override fun getIcon(flags: Int): Icon? {
        return GradleIcons.Gradle
      }
    }

  }
}
