// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
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

  val annotation = annotationHolder.createErrorAnnotation(nameElement, message("cannot.resolve", referenceName))
  annotation.highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
  generateCreateClassActions(ref).forEach(annotation::registerFix)
  generateAddImportActions(ref).forEach(annotation::registerFix)
  val fixRegistrar = AnnotationFixRegistrar(annotation)
  UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, fixRegistrar)
  QuickFixFactory.getInstance().registerOrderEntryFixes(fixRegistrar, ref)
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

  holder.createErrorAnnotation(nameElement, message("cannot.reference.non.static", qname))
}

internal val illegalJvmNameSymbols: Regex = "[.;\\[/<>:]".toRegex()
