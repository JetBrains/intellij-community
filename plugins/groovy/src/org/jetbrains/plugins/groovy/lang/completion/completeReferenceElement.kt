// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CompleteCodeReferenceElement")

package org.jetbrains.plugins.groovy.lang.completion

import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor.createClassLookupItems
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint.DeclarationKind.CLASS
import com.intellij.psi.scope.ElementClassHint.DeclarationKind.PACKAGE
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.Consumer
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil.setupLookupBuilder
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespacesAndComments
import org.jetbrains.plugins.groovy.lang.psi.util.treeWalkUp
import org.jetbrains.plugins.groovy.lang.resolve.imports.importedNameKey
import org.jetbrains.plugins.groovy.lang.resolve.processClasses
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrDelegatingScopeProcessorWithHints
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMembersFilteringProcessor
import org.jetbrains.plugins.groovy.lang.resolve.resolveClassFqn
import org.jetbrains.plugins.groovy.util.consumeAll

private typealias LookupConsumer = Consumer<LookupElement>

fun GrCodeReferenceElement.complete(matcher: PrefixMatcher, consumer: LookupConsumer) {
  val parent = parent

  val afterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(this)
  val processor = CompleteReferenceProcessor(matcher, consumer, afterNew)

  if (parent is GrImportStatement) {
    if (parent.isStatic) processClassDeclarations(processor)
    processPackageDeclarations(GrDelegatingScopeProcessorWithHints(processor, CLASS, PACKAGE))
    return
  }

  val packageProcessor = GrDelegatingScopeProcessorWithHints(processor, PACKAGE)
  if (parent is GrPackageDefinition) {
    processPackageDeclarations(packageProcessor)
    return
  }

  processTypeParameters(processor)

  val classProcessor = GrDelegatingScopeProcessorWithHints(processor, CLASS)
  processClasses(classProcessor, ResolveState.initial())
  if (qualifier != null) {
    processPackageDeclarations(classProcessor)
  }
  processPackageDeclarations(packageProcessor)
}

private fun GrCodeReferenceElement.processPackageDeclarations(processor: PsiScopeProcessor) {
  val qualifier = qualifier
  val qualifierFqn = if (qualifier == null) "" else qualifier.qualifiedReferenceName ?: return
  val parentPackage = JavaPsiFacade.getInstance(project).findPackage(qualifierFqn) ?: return
  parentPackage.processDeclarations(processor, ResolveState.initial(), null, this)
}

private fun GrCodeReferenceElement.processClassDeclarations(processor: PsiScopeProcessor) {
  val qualifier = qualifier ?: return
  val clazz = qualifier.resolveClassFqn() ?: return
  clazz.processDeclarations(StaticMembersFilteringProcessor(processor, null), ResolveState.initial(), null, this)
}

private fun GrCodeReferenceElement.processTypeParameters(processor: PsiScopeProcessor) {
  val typeParameterProcessor = object : GrDelegatingScopeProcessorWithHints(processor, emptySet()), GroovyResolveKind.Hint {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getHint(hintKey: Key<T>): T? = if (hintKey === GroovyResolveKind.HINT_KEY) this as T else super.getHint(hintKey)

    override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind === GroovyResolveKind.TYPE_PARAMETER
  }
  findTypeParameterListCandidate()?.processDeclarations(typeParameterProcessor, ResolveState.initial(), null, this)
  treeWalkUp(typeParameterProcessor, ResolveState.initial())
}

private class CompleteReferenceProcessor(
  private val matcher: PrefixMatcher,
  private val consumer: LookupConsumer,
  private val afterNew: Boolean
) : PsiScopeProcessor {

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    element as? PsiNamedElement ?: return true

    val name = element.name ?: return true
    val importedName = state.get(importedNameKey)

    if (element is PsiClass && importedName != null && importedName != name) {
      val builder = LookupElementBuilder.create(element, importedName).withPresentableText(importedName)
      val item = setupLookupBuilder(element, state.get(PsiSubstitutor.KEY), builder, null)
      consumer.consume(item)
    }
    else if (matcher.prefixMatches(name)) {
      if (element is PsiClass) {
        val items = createClassLookupItems(element, afterNew, GroovyClassNameInsertHandler(), Conditions.alwaysTrue())
        consumer.consumeAll(items)
      }
      else {
        val builder = LookupElementBuilder.create(element, name)
        val item = GroovyCompletionUtil.setupLookupBuilder(element, PsiSubstitutor.EMPTY, builder, null)
        consumer.consume(item)
      }
    }

    return true
  }
}

private fun GrCodeReferenceElement.findTypeParameterListCandidate(): GrTypeParameterList? {
  val typeElement = getRootTypeElement() ?: return null
  val parent = typeElement.parent
  return when (parent) {
    is GrTypeDefinitionBody -> skipWhitespacesAndComments(typeElement.prevSibling, false) as? GrTypeParameterList
    is GrVariableDeclaration -> {
      val errorElement = skipWhitespacesAndComments(typeElement.prevSibling, false) as? PsiErrorElement
      errorElement?.firstChild as? GrTypeParameterList
    }
    else -> null
  }
}

private fun GrCodeReferenceElement.getRootTypeElement(): GrTypeElement? {
  var current: PsiElement? = parent
  while (current.isTypeElementChild()) {
    val parent = current?.parent
    if (current is GrTypeElement) {
      if (!parent.isTypeElementChild()) return current
    }
    current = parent
  }
  return null
}

private fun PsiElement?.isTypeElementChild(): Boolean {
  return this is GrCodeReferenceElement || this is GrTypeArgumentList || this is GrTypeElement
}
