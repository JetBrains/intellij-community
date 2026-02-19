// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteral
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator
import org.jetbrains.plugins.groovy.lang.resolve.ast.AffectedMembersCache
import org.jetbrains.plugins.groovy.lang.resolve.ast.constructorGeneratingAnnotations
import org.jetbrains.plugins.groovy.lang.resolve.ast.getAffectedMembersCache

class GrAnnotationReferencingUnknownIdentifiers : BaseInspection() {

  override fun buildErrorString(vararg args: Any?): String {
    return GroovyBundle.message("inspection.message.couldnt.find.property.field.with.this.name")
  }

  companion object {
    private val IDENTIFIER_MATCHER = Regex("\\w+")
    private val DELIMITED_LIST_MATCHER = Regex("^(\\s*\\w+\\s*,)*\\s*\\w+\\s*\$")

    private fun iterateOverIdentifierList(value: PsiAnnotationMemberValue, identifiers: Set<String>): Iterable<TextRange> {
      when (value) {
        is PsiArrayInitializerMemberValue -> {
          val initializers = value.initializers
          return initializers.mapNotNull {
            (it as? PsiLiteral)?.takeUnless { literal -> literal.value in identifiers }?.textRangeInParent
          }
        }
        is PsiLiteral -> {
          val stringText: String = value.text ?: return emptyList()
          val internalRange = GroovyStringLiteralManipulator.getLiteralRange(stringText)
          val content = internalRange.substring(stringText).takeIf(DELIMITED_LIST_MATCHER::matches) ?: return emptyList()
          return IDENTIFIER_MATCHER.findAll(content)
            .filter { it.value !in identifiers }
            .map { TextRange(it.range.first + internalRange.startOffset, it.range.last + internalRange.startOffset + 1) }.asIterable()
        }
        else -> return emptyList()
      }
    }
  }


  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {

    private fun processAttribute(identifiers: Set<String>, annotation: GrAnnotation, attributeName: String) {
      val value = annotation.findAttributeValue(attributeName) ?: return
      // protection against default annotation values stored in annotation's .class file
      if (value.containingFile != annotation.containingFile) return
      for (range in iterateOverIdentifierList(value, identifiers)) {
        registerRangeError(value, range)
      }
    }

    override fun visitAnnotation(annotation: GrAnnotation) {
      super.visitAnnotation(annotation)
      if (!constructorGeneratingAnnotations.contains(annotation.qualifiedName)) return
      val cache = getAffectedMembersCache(annotation)
      val affectedMembers = cache.getAllAffectedMembers().mapNotNullTo(mutableSetOf(), AffectedMembersCache.Companion::getExternalName)
      processAttribute(affectedMembers, annotation, "includes")
      processAttribute(affectedMembers, annotation, "excludes")
    }
  }
}
