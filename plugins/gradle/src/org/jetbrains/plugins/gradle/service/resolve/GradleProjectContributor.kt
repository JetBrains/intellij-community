/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * @since 11/10/2016
 */
class GradleProjectContributor : GradleMethodContextContributor {

  companion object {
    val copySpecClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "copy", "copySpec"))
    val fileTreeClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "fileTree"))
    val filesClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "files"))

  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (copySpecClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_FILE_COPY_SPEC, closure), Closure.DELEGATE_FIRST)
    }
    if (fileTreeClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_FILE_CONFIGURABLE_FILE_TREE, closure), Closure.DELEGATE_FIRST)
    }
    if (filesClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_FILE_CONFIGURABLE_FILE_COLLECTION, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }

  override fun process(methodCallInfo: List<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    return GradleResolverUtil.processDeclarations(processor, state, place, GRADLE_API_PROJECT)
  }
}