// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil.isReassigned
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter.*
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isScriptField

internal class GroovyDeclarationHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? = psiFile.getGroovyFile()?.let {
    GroovyDeclarationHighlightingPass(psiFile, it, editor.document)
  }
}

private class GroovyDeclarationHighlightingPass(psiFile: PsiFile, groovyBaseFile: GroovyFileBase, document: Document) : GroovyHighlightingPass(psiFile, groovyBaseFile, document) {

  override fun doCollectInformation(progress: ProgressIndicator) {
    myGroovyBaseFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is GrReferenceElement<*>) {
          getReferenceHighlightingAttribute(element)?.let {
            addInfo(element.referenceNameElement ?: element, it)
          }
        }
        else if (element is GrNamedElement) {
          getDeclarationHighlightingAttribute(element)?.let {
            addInfo(element.nameIdentifierGroovy, it)
          }
        }
        else if (element !is GroovyPsiElement && element !is PsiErrorElement) {
          getElementAttribute(element)?.let {
            addInfo(element, it)
          }
        }
        super.visitElement(element)
      }
    })
    applyInformationInBackground()
  }
}

private fun getElementAttribute(element: PsiElement): TextAttributesKey? {
  val parent = element.parent
  if (parent is GrAnnotation && element.node.elementType === GroovyTokenTypes.mAT) {
    return ANNOTATION
  }
  else if (parent is GrAnnotationNameValuePair && parent.nameIdentifierGroovy === element) {
    return ANNOTATION_ATTRIBUTE_NAME
  }
  return null
}

private fun getReferenceHighlightingAttribute(reference: GrReferenceElement<*>): TextAttributesKey? {
  if (reference.isReferenceWithLiteralName()) return null // don't highlight literal references
  if (reference.isAnonymousClassReference()) return null

  val resolveResult = reference.advancedResolve()
  val resolved = resolveResult.element ?: return null

  val nameElement = reference.referenceNameElement
  if (nameElement != null && nameElement.isThisOrSuper()) {
    if (resolved is PsiMethod && resolved.isConstructor) {
      return null // don't highlight this() or super(), they are already highlighted
    }
    else if (resolved is PsiClass) {
      return if (shouldBeErased(nameElement)) {
        // keyword highlighting was erased, highlight the keyword back
        KEYWORD
      }
      else {
        // don't highlight, because highlighting of the keyword was not erased
        null
      }
    }
  }

  return if (resolved is PsiMethod) {
    if (resolved.isConstructor) {
      CONSTRUCTOR_CALL
    }
    else {
      val isStatic = resolved.hasModifierProperty(PsiModifier.STATIC)
      if (resolveResult.isInvokedOnProperty) {
        if (isStatic) STATIC_PROPERTY_REFERENCE else INSTANCE_PROPERTY_REFERENCE
      }
      else {
        if (isStatic) STATIC_METHOD_ACCESS else METHOD_CALL
      }
    }
  }
  else {
    getDeclarationHighlightingAttribute(resolved)
  }
}

private fun getDeclarationHighlightingAttribute(declaration: PsiElement): TextAttributesKey? {
  return when (declaration) {
    is GrLabeledStatement -> LABEL
    is PsiTypeParameter -> TYPE_PARAMETER
    is PsiMethod -> when {
      declaration.isConstructor -> CONSTRUCTOR_DECLARATION
      declaration.isMethodWithLiteralName() -> null
      else -> METHOD_DECLARATION
    }
    is PsiClass -> when {
      declaration is GrTraitTypeDefinition -> TRAIT_NAME
      declaration is GrAnonymousClassDefinition -> ANONYMOUS_CLASS_NAME
      declaration.isAnnotationType -> ANNOTATION
      declaration.isInterface -> INTERFACE_NAME
      declaration.isEnum -> ENUM_NAME
      else -> CLASS_REFERENCE
    }
    is PsiField -> when {
      declaration.hasModifierProperty(PsiModifier.STATIC) -> STATIC_FIELD
      else -> INSTANCE_FIELD
    }
    is GrParameter -> when {
      isReassigned(declaration) -> REASSIGNED_PARAMETER
      else -> PARAMETER
    }
    is GrVariable -> when {
      isScriptField(declaration) -> when {
        declaration.hasModifierProperty(PsiModifier.STATIC) -> STATIC_FIELD
        else -> INSTANCE_FIELD
      }
      else -> when {
        isReassigned(declaration) -> REASSIGNED_LOCAL_VARIABLE
        else -> LOCAL_VARIABLE
      }
    }
    else -> return null
  }
}
