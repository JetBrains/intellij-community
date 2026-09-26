// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.lang.properties.psi.I18nizedTextGenerator
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.ResourceBundleManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.actions.templates.generateDefaultBundleName

internal class IntelliJProjectResourceBundleManager(project: Project) : ResourceBundleManager(project) {
  override fun isActive(context: PsiFile): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(myProject)
  }

  override fun escapeValue(value: String): String {
    return value.replace("&", "\\&")
  }

  override fun suggestPropertiesFiles(contextModules: MutableSet<Module>): MutableList<String> {
    val preferredBundleNames = contextModules.mapTo(HashSet()) { generateDefaultBundleName(it) }
    val files = super.suggestPropertiesFiles(contextModules)
    val (preferredFiles, otherFiles) = files.partition {
      FileUtil.getNameWithoutExtension(PathUtil.getFileName(it)) in preferredBundleNames
    }
    return (preferredFiles + otherFiles).toMutableList()
  }

  override fun getI18nizedTextGenerator(): I18nizedTextGenerator {
    return object : I18nizedTextGenerator() {
      override fun getI18nizedText(propertyKey: String, propertiesFile: PropertiesFile?, context: PsiElement?): String {
        return getI18nizedConcatenationText(propertyKey, "", propertiesFile, context)
      }

      override fun getI18nizedConcatenationText(propertyKey: String,
                                                parametersString: String,
                                                propertiesFile: PropertiesFile?,
                                                context: PsiElement?): String {
        val bundleClassName = suggestBundleClassName(propertiesFile, context)
        val args = if (parametersString.isNotEmpty()) ", $parametersString" else ""
        return "$bundleClassName.message(\"$propertyKey\"$args)"
      }

      private fun suggestBundleClassName(propertiesFile: PropertiesFile?, context: PsiElement?): String {
        if (propertiesFile == null) return "UnknownBundle"
        val bundleName = propertiesFile.virtualFile.nameWithoutExtension
        val scope = context?.resolveScope ?: GlobalSearchScope.projectScope(myProject)
        val classesByName = PsiShortNamesCache.getInstance(myProject).getClassesByName(bundleName, scope)
        return classesByName.firstOrNull()?.qualifiedName ?: bundleName
      }
    }
  }

  override fun getResourceBundle(): PsiClass? = null
  override fun getTemplateName(): String? = null
  override fun getConcatenationTemplateName(): String? = null
  override fun canShowJavaCodeInfo(): Boolean = false
}