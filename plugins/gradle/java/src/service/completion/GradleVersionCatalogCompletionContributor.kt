// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor.Companion.DEPENDENCY_NOTATION
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase

class GradleVersionCatalogCompletionContributor {

  private enum class CompletionContext {
    LIBRARY_DEPENDENCY,
    PLUGIN
  }

  fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val containingFile = position.containingFile
    if (containingFile !is GroovyFileBase || !containingFile.name.endsWith(GradleConstants.EXTENSION)) {
      return
    }
    val resolvedQualifier = position.parent.asSafely<GrReferenceExpression>()?.qualifier?.asSafely<GrReferenceElement<*>>()?.resolve() ?: return
    val longNames = when (resolvedQualifier) {
      is GroovyPropertyBase -> {
        val type = resolvedQualifier.propertyType as? PsiClassType ?: return
        if (!type.className.startsWith("LibrariesFor")) {
          return
        }
        val clazz = type.resolve() ?: return
        computeCompletionCandidates(clazz, position)
      }
      is PsiMethod -> {
        if (resolvedQualifier.containingClass?.name?.startsWith("LibrariesFor") != true) {
          return
        }
        computeCompletionCandidates(resolvedQualifier, position)
      }
      else -> return
    }
    for ((longName, description) in longNames) {
      val builder = PrioritizedLookupElement.withPriority(LookupElementBuilder.create(longName).withIcon(GradleIcons.Gradle).withTypeText(description), 101.0)
      result.addElement(builder)
    }
  }

  private fun guessCompletionContext(position: PsiElement) : CompletionContext? {
    val parentCalls = position.parentsOfType<GrMethodCall>()
    for (parent in parentCalls) {
      val parentMethod = parent.resolveMethod()
      if (parentMethod is OriginInfoAwareElement && parentMethod.originInfo == DEPENDENCY_NOTATION) {
        return CompletionContext.LIBRARY_DEPENDENCY
      }
      if (parent.invokedExpression.text == "alias" && parent.parentOfType<GrMethodCall>()?.resolveMethod()?.name == "plugins") {
        return CompletionContext.PLUGIN
      }
    }
    return null
  }

  private fun computeCompletionCandidates(root: PsiElement, position: PsiElement) : List<Dependency> {
    return when (root) {
      is PsiClass -> when (guessCompletionContext(position)) {
        CompletionContext.LIBRARY_DEPENDENCY -> {
          val methodCollector = mutableListOf<Dependency>()
          val bundlesMethod = root.findMethodsByName("getBundles").singleOrNull().asSafely<PsiMethod>() ?: return emptyList()
          methodCollector.addAll(concatMethodsDeep(bundlesMethod))
          for (method in root.methods) {
            if (method.returnType?.canonicalText?.endsWith("LibraryAccessors") == true) {
              methodCollector.addAll(concatMethodsDeep(method))
            }
          }
          methodCollector
        }
        CompletionContext.PLUGIN -> {
          val pluginMethod = root.findMethodsByName("getPlugins").singleOrNull().asSafely<PsiMethod>() ?: return emptyList()
          concatMethodsDeep(pluginMethod)
        }
        null -> emptyList()
      }
      is PsiMethod -> concatMethodsDeep(root, false)
      else -> emptyList()
    }
  }

  private data class Dependency(val name: String, val notation: String?)

  private fun concatMethodsDeep(method: PsiMethod, includeFirst: Boolean = true) : List<Dependency> {
    val methodPropertyName = GroovyPropertyUtils.getPropertyName(method) ?: return emptyList()
    val returnType = method.returnType as? PsiClassType ?: return emptyList()
    val clazz = returnType.resolve() ?: return emptyList()
    if (clazz.qualifiedName == GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER) {
      val notation = parseNotation(method)
      return listOf(Dependency(methodPropertyName, notation))
    } else {
      val newAccessors = mutableListOf<Dependency>()
      for (codeMethod in clazz.methods) {
        newAccessors.addAll(concatMethodsDeep(codeMethod))
      }
      return if (includeFirst) newAccessors.map { it.copy(name = "$methodPropertyName.${it.name}") } else newAccessors
    }
  }

  private fun parseNotation(method: PsiMethod) : String? {
    val docComment = method.docComment ?: return null
    val regex = Regex("\\(.*?\\)")
    val notation = regex.find(docComment.text)?.value ?: return null
    return notation.substring(1, notation.lastIndex)
  }

}