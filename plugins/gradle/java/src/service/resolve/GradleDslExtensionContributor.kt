// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import groovy.lang.Closure
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

/**
 * Provides information on extensions registered to existing extensions in a gradle project.
 * Then registers the relevant properties and configure methods for the extensions.
 * <p>
 *   This is different from the <code>GradleProjectExtensionContributor</code> in that it does not
 *   require the extension to be a project extension.
 *
 *   This is also different from the <code>GradleExtensionsContributor</code> in that it does not
 *   require the extension to be a convention.
 * </p>
 */
class GradleDslExtensionContributor : AbstractGradleExtensionContributor() {

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (qualifierType !is GradleExtensionType) return
    if (aClass == null) return;

    val qualifiedExtensionType : GradleExtensionType = qualifierType

    val processMethods = processor.shouldProcessMethods()
    val processProperties = processor.shouldProcessProperties()
    if (!processMethods && !processProperties) {
      return
    }

    val containingFile = place.containingFile
    val extensionsData = GradlePropertyExtensionsContributor.getExtensionsFor(containingFile) ?: return

    val elementName = processor.getName(state) ?: return;
    val name = "${qualifiedExtensionType.path}.${elementName}"
    val allExtensions = extensionsData.extensions
    val extensions = listOf(allExtensions[name] ?: return)

    processExtensions(extensions, containingFile, place, processor, aClass, state)
  }
}
