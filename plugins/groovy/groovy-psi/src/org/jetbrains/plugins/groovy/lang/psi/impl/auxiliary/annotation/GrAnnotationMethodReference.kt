// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult

internal class GrAnnotationMethodReference(private val element: GrAnnotationNameValuePair) : GroovyReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange {
    val nameId = element.nameIdentifierGroovy!!
    return nameId.textRange.shiftLeft(element.textRange.startOffset)
  }

  override fun getCanonicalText(): String = rangeInElement.substring(element.text)

  override fun isReferenceTo(element: PsiElement): Boolean {
    return element is PsiMethod && element.getManager().areElementsEquivalent(element, resolve())
  }

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val annotation = PsiImplUtil.getAnnotation(element) ?: return emptyList()
    val ref = annotation.classReference
    val resolved = ref.resolve() as? PsiClass ?: return emptyList()
    val name = element.name ?: DEFAULT_REFERENCED_METHOD_NAME
    val collector = GrAnnotationCollector.findAnnotationCollector(resolved)
    if (collector != null) {
      return multiResolveFromAlias(annotation, name, collector)
    }
    else if (resolved.isAnnotationType) {
      return resolved.findMethodsByName(name, false).map(::ElementResolveResult)
    }
    return emptyList()
  }

  private fun multiResolveFromAlias(alias: GrAnnotation, name: String, annotationCollector: PsiAnnotation): List<GroovyResolveResult> {
    val annotations = mutableListOf<GrAnnotation>()
    GrAnnotationCollector.collectAnnotations(annotations, alias, annotationCollector)
    val result = mutableListOf<GroovyResolveResult>()
    for (annotation in annotations) {
      val clazz = annotation.classReference.resolve() as? PsiClass ?: continue
      if (!clazz.isAnnotationType) continue
      if (clazz.qualifiedName == GROOVY_TRANSFORM_ANNOTATION_COLLECTOR) continue
      clazz.findMethodsByName(name, false).mapTo(result, ::ElementResolveResult)
    }
    return result
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    val nameElement = element.nameIdentifierGroovy
    val newNameNode = GroovyPsiElementFactory.getInstance(element.project).createReferenceNameFromText(newElementName).node!!
    if (nameElement != null) {
      val node = nameElement.node
      element.node.replaceChild(node, newNameNode)
    }
    else {
      val anchorBefore = element.firstChild?.node
      element.node.addLeaf(GroovyTokenTypes.mASSIGN, "=", anchorBefore)
      element.node.addChild(newNameNode, anchorBefore)
    }
    return element
  }

  override fun bindToElement(element: PsiElement): PsiElement = throw IncorrectOperationException("NYI")

  override fun isSoft(): Boolean = false
}
