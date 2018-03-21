// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo


/**
 * @author Vladislav.Soroka
 * @since 11/18/13
 */
class GradleIdeaPluginScriptContributor : GradleMethodContextContributor {

  companion object {
    val IDEA_METHOD = "idea"
    val IDEA_MODEL_FQN = "org.gradle.plugins.ide.idea.model.IdeaModel"
    val IDEA_PROJECT_FQN = "org.gradle.plugins.ide.idea.model.IdeaProject"
    val IDEA_MODULE_FQN = "org.gradle.plugins.ide.idea.model.IdeaModule"
    val IDEA_MODULE_IML_FQN = "org.gradle.plugins.ide.idea.model.IdeaModuleIml"
    val IDE_XML_MERGER_FQN = "org.gradle.plugins.ide.api.XmlFileContentMerger"
    val IDE_FILE_MERGER_FQN = "org.gradle.plugins.ide.api.FileContentMerger"
    val IDEA_XML_MODULE_FQN = "org.gradle.plugins.ide.idea.model.Module"
    val IDEA_XML_PROJECT_FQN = "org.gradle.plugins.ide.idea.model.Project"
    val ideaClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, IDEA_METHOD))
    val ideaProjectClosure = groovyClosure().inMethod(psiMethod(IDEA_MODEL_FQN, "project"))
    val ideaIprClosure = groovyClosure().inMethod(psiMethod(IDEA_PROJECT_FQN, "ipr"))
    val ideaModuleClosure = groovyClosure().inMethod(psiMethod(IDEA_MODEL_FQN, "module"))
    val ideaImlClosure = groovyClosure().inMethod(psiMethod(IDEA_MODULE_FQN, "iml"))
    val ideaBeforeMergedClosure = groovyClosure().inMethod(psiMethod(IDE_FILE_MERGER_FQN, "beforeMerged"))
    val ideaWhenMergedClosure = groovyClosure().inMethod(psiMethod(IDE_FILE_MERGER_FQN, "whenMerged"))
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (ideaClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(IDEA_MODEL_FQN, closure), Closure.DELEGATE_FIRST)
    }
    if (ideaImlClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(IDEA_MODULE_IML_FQN, closure), Closure.DELEGATE_FIRST)
    }
    if (ideaProjectClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(IDEA_PROJECT_FQN, closure), Closure.DELEGATE_FIRST)
    }
    if (ideaModuleClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(IDEA_MODULE_FQN, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val ideaExtension = GradleExtensionsSettings.GradleExtension().apply {
      name = IDEA_METHOD
      rootTypeFqn = IDEA_MODEL_FQN
    }
    if (!processExtension(processor, state, place, ideaExtension)) return false

    val psiManager = GroovyPsiManager.getInstance(place.project)
    if (psiElement().inside(ideaIprClosure).inside(ideaProjectClosure).accepts(place)) {
        if (GradleResolverUtil.processDeclarations(processor, state, place, IDE_XML_MERGER_FQN)) return false
    }
    return true
  }
}