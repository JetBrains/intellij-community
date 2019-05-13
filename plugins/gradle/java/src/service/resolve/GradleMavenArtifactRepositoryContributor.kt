// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.patterns.PsiJavaElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyClosurePattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * Provides gradle MavenArtifactRepository DSL resolving contributor.
 *
 *  e.g.
 *  repositories {
 *     maven {
 *       url "http://snapshots.repository.codehaus.org/"
 *     }
 *  }
 *
 * @author Vladislav.Soroka
 * @since 10/21/13
 */
class GradleMavenArtifactRepositoryContributor : GradleMethodContextContributor {
  companion object {
    val repositoriesClosure: GroovyClosurePattern = groovyClosure().inMethod(or(psiMethod(GRADLE_API_PROJECT, "repositories"),
                                                                                psiMethod(GRADLE_API_SCRIPT_HANDLER, "repositories")))
    val repositoryClosure: PsiJavaElementPattern.Capture<PsiElement> = psiElement().andOr(
      groovyClosure().withAncestor(2, repositoriesClosure),
      groovyClosure().inMethod(psiMethod(GRADLE_API_REPOSITORY_HANDLER, "maven")))
  }

  override fun process(methodCallInfo: List<String>,
                       processor: PsiScopeProcessor,
                       state: ResolveState,
                       place: PsiElement): Boolean {
    if (methodCallInfo.isNotEmpty() && psiElement().inside(repositoryClosure).accepts(place)) {
      if (!GradleResolverUtil.processDeclarations(
        processor, state, place, GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)) return false
    }
    return true
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (repositoriesClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_REPOSITORY_HANDLER, closure), Closure.DELEGATE_FIRST)
    }
//    if (repositoryClosure.accepts(closure)) {
//      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY, closure), DELEGATE_FIRST)
//    }
    return null
  }
}
