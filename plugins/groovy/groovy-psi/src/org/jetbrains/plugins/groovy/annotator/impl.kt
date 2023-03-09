// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil.isInnerClass
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.annotator.GrReferenceHighlighterFactory.shouldHighlight
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.generateAddImportActions
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.generateCreateClassActions
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.hasArguments
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker.isInStaticContext
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.hasEnclosingInstanceInScope

fun checkUnresolvedCodeReference(ref: GrCodeReferenceElement, annotationHolder: AnnotationHolder) {
  if (!shouldHighlight(annotationHolder.currentAnnotationSession.file)) return

  if (ref.parentOfType<GroovyDocPsiElement>() != null) return
  if (ref.parent is GrPackageDefinition) return

  val nameElement = ref.referenceNameElement ?: return
  val referenceName = ref.referenceName ?: return

  if (isResolvedStaticImport(ref)) return
  if (ref.resolve() != null) return

  val builder = annotationHolder.newAnnotation(HighlightSeverity.ERROR, message("cannot.resolve", referenceName)).range(nameElement)
    .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
  generateCreateClassActions(ref).forEach { builder.withFix(it) }
  generateAddImportActions(ref).forEach { builder.withFix(it) }
  val fixRegistrar = AnnotationFixRegistrar(builder)
  UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, fixRegistrar)
  val registrar = ArrayList<IntentionAction>()
  QuickFixFactory.getInstance().registerOrderEntryFixes(ref, registrar)
  for (fix in registrar) {
    fixRegistrar.register(fix)
  }
  builder.create()
}

private fun isResolvedStaticImport(refElement: GrCodeReferenceElement): Boolean {
  val parent = refElement.parent
  return parent is GrImportStatement &&
         parent.isStatic &&
         refElement.multiResolve(false).isNotEmpty()
}


fun checkInnerClassReferenceFromInstanceContext(ref: GrCodeReferenceElement, holder: AnnotationHolder) {
  val nameElement = ref.referenceNameElement ?: return

  val parent = ref.parent
  if (parent !is GrNewExpression || hasArguments(parent)) return

  if (!isInStaticContext(ref)) return

  val resolved = ref.resolve() as? PsiClass ?: return
  if (!isInnerClass(resolved)) return
  val qname = resolved.qualifiedName ?: return

  val outerClass = resolved.containingClass ?: return
  if (hasEnclosingInstanceInScope(outerClass, parent, true)) return

  holder.newAnnotation(HighlightSeverity.ERROR, message("cannot.reference.non.static", qname)).range(nameElement).create()
}

internal val illegalJvmNameSymbols: Regex = "[.;\\[/<>]".toRegex()
