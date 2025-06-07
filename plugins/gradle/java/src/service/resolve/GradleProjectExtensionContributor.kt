// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradleProjectExtensionContributor : AbstractGradleExtensionContributor() {

  override fun getParentClassName(): String = GRADLE_API_PROJECT

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (qualifierType !is GradleProjectAwareType) return
    if (aClass == null) return;

    val processMethods = processor.shouldProcessMethods()
    val processProperties = processor.shouldProcessProperties()
    if (!processMethods && !processProperties) {
      return
    }

    val containingFile = place.containingFile
    val extensionsData = GradlePropertyExtensionsContributor.getExtensionsFor(containingFile) ?: return

    val name = processor.getName(state)
    val allExtensions = extensionsData.extensions
    val extensions = if (name == null) allExtensions.values else listOf(allExtensions[name] ?: return)

    /**
     *     val module = ModuleUtilCore.findModuleForPsiElement(place)
     *     val versionCatalogNames = if (module != null) {
     *       getVersionCatalogFiles(module).keys
     *     } else {
     *       emptySet()
     *     }
     */

    processExtensions(extensions, containingFile, place, processor, aClass, state)
  }
}
