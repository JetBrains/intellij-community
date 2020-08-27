// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralValue
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.resolve.ast.AffectedMembersCache
import org.jetbrains.plugins.groovy.lang.resolve.ast.constructorGeneratingAnnotations

class GrAnnotationReferencingUnknownIdentifiers : BaseInspection() {

  override fun buildErrorString(vararg args: Any?): String? {
    return GroovyBundle.message("inspection.message.couldnt.find.property.field.with.this.name")
  }

  companion object {
    fun iterateOverIdentifierList(value: PsiAnnotationMemberValue): Iterable<PsiLiteralValue> {
      if (value is PsiArrayInitializerMemberValue) {
        val initializers = value.initializers
        return initializers.mapNotNull {
          it as? PsiLiteralValue
        }
      }
      else {
        return emptyList()
      }
    }
  }


  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {

    private fun processAttribute(identifiers: Set<String>, annotation: GrAnnotation, attributeName: String) {
      val value = annotation.findAttributeValue(attributeName) ?: return
      for (identifier in iterateOverIdentifierList(value)) {
        val name = identifier.value as? String ?: continue
        if (identifiers.contains(name)) continue
        registerError(identifier)
      }
    }

    override fun visitAnnotation(annotation: GrAnnotation) {
      super.visitAnnotation(annotation)
      if (!constructorGeneratingAnnotations.contains(annotation.qualifiedName)) return
      val cache = AffectedMembersCache(annotation)
      val affectedMembers = cache.getAllAffectedMembers().mapNotNullTo(mutableSetOf()) { it.name }
      processAttribute(affectedMembers, annotation, "includes")
      processAttribute(affectedMembers, annotation, "excludes")
    }
  }
}