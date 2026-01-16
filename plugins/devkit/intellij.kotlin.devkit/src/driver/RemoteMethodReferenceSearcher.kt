// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.driver

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.EMPTY_SCOPE
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.uast.*

internal class RemoteMethodReferenceSearcher :
  QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>() {
  override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val targetMethod = queryParameters.method
    val methodName = ReadAction.compute<String, Throwable> { targetMethod.name }

    queryParameters.optimizer.searchWord(
      methodName,
      ReadAction.compute<SearchScope, RuntimeException> {
        if (targetMethod.hasModifier(JvmModifier.PRIVATE)
            || targetMethod.hasModifier(JvmModifier.PACKAGE_LOCAL)
            || targetMethod.hasModifier(JvmModifier.PROTECTED)
        ) {
          return@compute EMPTY_SCOPE
        }

        // do not search for self
        val searchableClass = targetMethod.containingClass ?: return@compute EMPTY_SCOPE
        if (isRemoteInterface(searchableClass)) return@compute EMPTY_SCOPE

        val project = targetMethod.project
        val file = searchableClass.containingFile?.virtualFile ?: return@compute EMPTY_SCOPE
        if (TestSourcesFilter.isTestSources(file, project)
            || FileIndexFacade.getInstance(project).isInLibrary(file)
        ) {
          return@compute EMPTY_SCOPE
        }

        val remoteClass = JavaPsiFacade.getInstance(project)
                            .findClass(REMOTE_ANNOTATION_FQN, allScope(project))
                          ?: return@compute EMPTY_SCOPE

        // we don't care about resolve scope of the method itself
        // only search in project sources; when annotation comes from libraries, it may have a huge .useScope
        remoteClass.useScope
          .intersectWith(queryParameters.scopeDeterminedByUser)
          .intersectWith(GlobalSearchScope.projectScope(project))
      },
      UsageSearchContext.IN_CODE,
      true,
      targetMethod,
      object : RequestResultProcessor() {
        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
          // short-circuit without UAST conversion
          if (element !is KtNamedFunction) return true
          if (element.valueParameters.size != targetMethod.parameters.size) return true
          val holderClass = element.containingClassOrObject ?: return true
          if (holderClass !is KtClass || !holderClass.isInterface()) return true

          // target checks
          val uMethod = element.toUElement(UMethod::class.java) ?: return true
          val uClass = uMethod.getContainingUClass() ?: return true
          val psiMethodFound = uMethod.javaPsi
          val psiClass = psiMethodFound.containingClass ?: return true
          if (!isRemoteInterface(psiClass)) return true

          val baseClass = getTargetRemoteClass(element.project, uClass) ?: return true

          val searchableClass = targetMethod.containingClass
          if (!isInheritorOrSelf(searchableClass, baseClass, true)
              && !isInheritorOrSelf(baseClass, searchableClass, true)) {
            return true
          }

          val reference = PsiReferenceBase.createSelfReference(
            element,
            TextRange(offsetInElement, offsetInElement + methodName.length),
            targetMethod
          )

          return consumer.process(reference)
        }
      }
    )
  }
}

internal fun isRemoteInterface(clazz: PsiClass?): Boolean {
  return clazz != null && clazz.isInterface && AnnotationUtil.isAnnotated(clazz, REMOTE_ANNOTATION_FQN, 0)
}

internal fun getTargetRemoteClass(project: Project, uClass: UClass): PsiClass? {
  val targetClassFqn = uClass.uAnnotations
                         .find { it.qualifiedName == REMOTE_ANNOTATION_FQN }
                         ?.findAttributeValue("value")
                         ?.evaluateString()
                       ?: return null

  return JavaPsiFacade.getInstance(project)
    .findClass(targetClassFqn, allScope(project))
}