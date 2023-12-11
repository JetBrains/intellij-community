// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiJavaElementPattern
import com.intellij.psi.*
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.FilterPattern
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ConcurrentFactoryMap
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.*
import java.util.concurrent.ConcurrentMap

internal class JUnitReferenceContributor : PsiReferenceContributor() {
  private val enumSourceNamesPattern: PsiElementPattern.Capture<PsiLanguageInjectionHost>
    get() = getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE, "names")
      .withAncestor(4, PlatformPatterns.psiElement(PsiAnnotation::class.java).and(PsiJavaElementPattern.Capture(
        object : InitialPatternCondition<PsiAnnotation>(PsiAnnotation::class.java) {
          override fun accepts(o: Any?, context: ProcessingContext): Boolean {
            if (o is PsiAnnotation) {
              val mode = o.findAttributeValue("mode")
              if (mode is PsiReferenceExpression) {
                val referenceName = mode.referenceName
                return "INCLUDE" == referenceName || "EXCLUDE" == referenceName
              }
            }
            return false
          }
        })))

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      getElementPattern(
        JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<MethodSourceReference> {
        return arrayOf(MethodSourceReference((element as PsiLanguageInjectionHost)))
      }
    })
    registrar.registerReferenceProvider(
      getElementPattern(
        JUnitCommonClassNames.ORG_JUNIT_JUPITER_CONDITION_PROVIDER_ENABLED_IF,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<DisabledIfEnabledIfReference> {
        return arrayOf(DisabledIfEnabledIfReference((element as PsiLanguageInjectionHost)))
      }
    })
    registrar.registerReferenceProvider(
      getElementPattern(
        JUnitCommonClassNames.ORG_JUNIT_JUPITER_CONDITION_PROVIDER_DISABLED_IF,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<DisabledIfEnabledIfReference> {
        return arrayOf(DisabledIfEnabledIfReference((element as PsiLanguageInjectionHost)))
      }
    })
    registrar.registerReferenceProvider(enumSourceNamesPattern, object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<EnumSourceReference> {
        return arrayOf(EnumSourceReference((element as PsiLanguageInjectionHost)))
      }
    })
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE,
                                                          "resources"), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        return FileReferenceSet.createSet(element, false, false, false).allReferences
      }
    })
  }

  private class TestAnnotationFilter(private val myAnnotation: String, private val myParameterName: @NonNls String) : ElementFilter {
    override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
      if (context == null) return false
      if (isDumb(context.project)) return false
      if (getMapOfAnnotationClasses(context.containingFile)[myAnnotation] == null) return false
      val type = context.toUElement(UElement::class.java) ?: return false
      var contextParent = type.uastParent
      var parameterFound = false
      var i = 0
      while (i < 5 && contextParent != null) {
        if (contextParent is UFile
            || contextParent is UDeclaration
            || contextParent is UDeclarationsExpression
            || contextParent is UJumpExpression
            || contextParent is UBlockExpression
        ) return false
        if (contextParent is UNamedExpression) {
          val name = contextParent.name ?: PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
          if (myParameterName != name) return false
          parameterFound = true
        }
        if (contextParent is UAnnotation) return parameterFound && myAnnotation == contextParent.qualifiedName
        contextParent = contextParent.uastParent
        i++
      }
      return false
    }

    override fun isClassAcceptable(hintClass: Class<*>): Boolean {
      return PsiLanguageInjectionHost::class.java.isAssignableFrom(hintClass)
    }

    private fun getMapOfAnnotationClasses(containingFile: PsiFile): ConcurrentMap<String, PsiClass?> {
      return CachedValuesManager.getCachedValue(containingFile) {
        val project = containingFile.project
        CachedValueProvider.Result(
          ConcurrentFactoryMap.createMap { annoName ->
            JavaPsiFacade.getInstance(project).findClass(annoName, containingFile.resolveScope)
          },
          ProjectRootModificationTracker.getInstance(project))
      }
    }
  }

  private fun getElementPattern(annotation: String, paramName: String): PsiElementPattern.Capture<PsiLanguageInjectionHost> {
    return PlatformPatterns.psiElement(
      PsiLanguageInjectionHost::class.java).and(FilterPattern(TestAnnotationFilter(annotation, paramName)))
  }
}
