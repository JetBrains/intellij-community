// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddToPermitsList
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.getAllPermittedClassElements
import org.jetbrains.plugins.groovy.lang.psi.util.getAllPermittedClasses
import org.jetbrains.plugins.groovy.lang.psi.util.getSealedElement
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class GroovyAnnotator40(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
    checkModifiers(typeDefinition)
    typeDefinition.permitsClause?.let { typeDefinition.checkPermitsClause(it) }
    typeDefinition.extendsClause?.let { typeDefinition.checkExtendsClause(it) }
    typeDefinition.implementsClause?.let { typeDefinition.checkImplementsClause(it) }
    super.visitTypeDefinition(typeDefinition)
  }

  private fun checkModifiers(owner: GrTypeDefinition) {
    val modifierList = owner.modifierList ?: return
    if (owner is GrEnumTypeDefinition) {
      checkEnum(modifierList)
      return
    }
    val sealed = modifierList.getModifier(GrModifier.SEALED)
    val nonSealed = modifierList.getModifier(GrModifier.NON_SEALED)
    val final = modifierList.getModifier(GrModifier.FINAL)
    val sealedAnno = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED)
    val nonSealedAnno = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_NON_SEALED)
    checkExclusiveness(sealed, nonSealed, final, sealedAnno, nonSealedAnno)
    checkSealed(owner, sealed ?: sealedAnno)
    checkNonSealed(owner, nonSealed ?: nonSealedAnno)
  }

  private fun checkExclusiveness(vararg exclusiveElement: PsiElement?) =
    exclusiveElement.filterNotNull().takeIf { it.size > 1 }?.forEach(this::createExclusivenessAnnotation)

  private fun createExclusivenessAnnotation(element: PsiElement) {
    if (element is GrAnnotation) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message(
        "inspection.message.only.one.final.sealed.non.sealed.should.be.applied.to.class")).range(element).create()
    }
    else {
      checkModifierIsNotAllowed(element.parent as GrModifierList, element.text, GroovyBundle.message(
        "inspection.message.only.one.final.sealed.non.sealed.should.be.applied.to.class"), holder)
    }
  }

  private fun checkSealed(owner: GrTypeDefinition, sealedElement: PsiElement?) {
    if (sealedElement == null) return
    if (getAllPermittedClassElements(owner).isEmpty()) {
      if (owner.isInterface) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.interface.has.no.explicit.or.implicit.implementors",
                                                  owner.name)).range(sealedElement).create()
      }
      else {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.class.has.no.explicit.or.implicit.subclasses", owner.name)).range(
          sealedElement).create()
      }
    }
  }

  private fun checkNonSealed(owner: GrTypeDefinition, nonSealedElement: PsiElement?) {
    if (nonSealedElement == null) return
    val referencedClasses = listOfNotNull(owner.extendsClause, owner.implementsClause).flatMap { it.referencedTypes.asList() }
    if (referencedClasses.mapNotNull(PsiClassType::resolve).all { it !is GrTypeDefinition || it.getSealedElement() == null }) {
      if (owner.isInterface) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.interface.cannot.be.non.sealed.without.sealed.parent",
                                                  owner.name)).range(nonSealedElement).create()
      }
      else {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.class.cannot.be.non.sealed.without.sealed.parent",
                                                  owner.name)).range(nonSealedElement).create()
      }
    }
  }

  private fun checkEnum(modifierList: GrModifierList) {
    checkModifierIsNotAllowed(modifierList, GrModifier.SEALED,
                              GroovyBundle.message("inspection.message.modifier.sealed.cannot.be.applied.to.enum.class"), holder)
    checkModifierIsNotAllowed(modifierList, GrModifier.NON_SEALED,
                              GroovyBundle.message("inspection.message.modifier.non.sealed.cannot.be.applied.to.enum.class"), holder)
    modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED)?.let {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message(
        "inspection.message.annotation.sealed.cannot.be.applied.to.enum.class")).range(it).create()
    }
    modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_NON_SEALED)?.let {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message(
        "inspection.message.annotation.non.sealed.cannot.be.applied.to.enum.class")).range(it).create()
    }
  }

  private fun GrTypeDefinition.checkPermitsClause(permitsClause: GrPermitsClause) {
    if (permitsClause.keyword == null || permitsClause.referencedTypes.isEmpty()) {
      return super.visitPermitsClause(permitsClause)
    }
    if (modifierList?.hasModifierProperty(GrModifier.SEALED) == true) return
    val fix = GrModifierFix(this, GrModifier.SEALED, false, true) {
      it.startElement.parentOfType<PsiModifierListOwner>()?.modifierList
    }
    val builder = holder
      .newAnnotation(HighlightSeverity.ERROR,
                     GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", name))
      .range(permitsClause.keyword!!)
    registerLocalFix(builder, fix, permitsClause,
                     GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", name),
                     ProblemHighlightType.ERROR, permitsClause.textRange)
      .create()
  }

  private fun GrTypeDefinition.checkExtendsClause(extendsClause: GrExtendsClause) {
    checkPermissions(extendsClause)
  }

  private fun GrTypeDefinition.checkImplementsClause(implementsClause: GrImplementsClause) {
    checkPermissions(implementsClause)
  }

  private fun GrTypeDefinition.checkPermissions(referenceClause: GrReferenceList) {
    val ownerType = type()
    for ((type, element) in referenceClause.referencedTypes.zip(referenceClause.referenceElementsGroovy)) {
      val baseClass = type.resolve()?.takeIf { it is GrTypeDefinition && it.getSealedElement() != null } as? GrTypeDefinition ?: continue
      val permittedClasses = getAllPermittedClasses(baseClass)
      if (this in permittedClasses) {
        continue
      }
      holder
        .newAnnotation(HighlightSeverity.ERROR,
                       GroovyBundle.message("inspection.message.not.allowed.in.sealed.hierarchy", ownerType.className))
        .range(element)
        .apply {
          if (baseClass.hasModifierProperty(GrModifier.SEALED)) {
            baseClass.permitsClause?.let { withFix(AddToPermitsList(this@checkPermissions, it)) }
          }
        }
        .create()
    }
  }
}
