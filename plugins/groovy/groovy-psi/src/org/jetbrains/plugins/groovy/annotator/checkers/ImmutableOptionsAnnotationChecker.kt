// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.transformations.immutable.GROOVY_TRANSFORM_IMMUTABLE_OPTIONS
import org.jetbrains.plugins.groovy.transformations.immutable.KNOWN_IMMUTABLES_OPTION

class ImmutableOptionsAnnotationChecker : CustomAnnotationChecker() {
  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (GROOVY_TRANSFORM_IMMUTABLE_OPTIONS != annotation.qualifiedName) return false
    val containingClass = PsiTreeUtil.getParentOfType(annotation, GrTypeDefinition::class.java) ?: return false
    val immutableFieldsList = AnnotationUtil.findDeclaredAttribute(annotation, KNOWN_IMMUTABLES_OPTION)?.value ?: return false

    val fields = (immutableFieldsList as? GrAnnotationArrayInitializer)?.initializers ?: return false
    for (literal in fields.filterIsInstance<GrLiteral>()) {
      val value = literal.value as? String ?: continue
      val target : PsiModifierListOwner? =
        containingClass.findCodeFieldByName(value, false) as? GrField
        ?: containingClass.asSafely<GrRecordDefinition>()?.parameters?.find { it.name == value }

      if (target == null || (target is GrField && !target.isProperty) || target.hasModifierProperty(PsiModifier.STATIC)) {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("immutable.options.property.not.exist", value)).range(literal).create()
      }
    }
    return false
  }
}