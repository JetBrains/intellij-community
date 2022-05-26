// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddToPermitsList
import org.jetbrains.plugins.groovy.annotator.intentions.GrChangeModifiersFix
import org.jetbrains.plugins.groovy.annotator.intentions.GrReplaceReturnWithYield
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.transformations.immutable.isImmutable

class GroovyAnnotator40(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  companion object {
    private val CONFLICTING_MODIFIERS =
      listOf(
        GrModifier.SEALED,
        GrModifier.NON_SEALED,
        GrModifier.FINAL,
        GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED,
        GroovyCommonClassNames.GROOVY_TRANSFORM_NON_SEALED,
      )

    private val FORBIDDEN_FOR_RECORD = listOf(
      GrModifier.SEALED,
      GrModifier.NON_SEALED,
      GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED,
      GroovyCommonClassNames.GROOVY_TRANSFORM_NON_SEALED,
    )
  }

  override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
    checkSealingModifiers(typeDefinition)
    typeDefinition.permitsClause?.let { typeDefinition.checkPermitsClause(it) }
    typeDefinition.extendsClause?.let { typeDefinition.checkExtendsClause(it) }
    typeDefinition.implementsClause?.let { typeDefinition.checkImplementsClause(it) }
    super.visitTypeDefinition(typeDefinition)
  }

  override fun visitRecordDefinition(recordDefinition: GrRecordDefinition) {
    recordDefinition.modifierList?.let { checkRecordModifiers(it) }
    for (formalParameter in recordDefinition.parameters) {
      checkFormalRecordParameters(formalParameter, recordDefinition)
    }
    super.visitRecordDefinition(recordDefinition)
  }

  private fun checkFormalRecordParameters(formalParameter: GrParameter, record : GrRecordDefinition) {
    if (formalParameter.hasModifierProperty(GrModifier.STATIC)) {
      return
    }
    val field = record.fields.find { it.name == formalParameter.name } ?: return
    if (!isImmutable(field)) {
      holder
        .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.record.parameter.should.be.immutable", formalParameter.name))
        .range(formalParameter.nameIdentifierGroovy)
        .create()
    }
  }

  private fun checkRecordModifiers(recordDefinition: GrModifierList) {
    recordDefinition.getModifier(GrModifier.SEALED)?.forbidForRecord()
    recordDefinition.getModifier(GrModifier.NON_SEALED)?.forbidForRecord()
    recordDefinition.annotations.find { it.qualifiedName == GroovyCommonClassNames.GROOVY_TRANSFORM_SEALED }?.forbidForRecord()
    recordDefinition.annotations.find { it.qualifiedName == GroovyCommonClassNames.GROOVY_TRANSFORM_NON_SEALED }?.forbidForRecord()
  }

  private fun PsiElement.forbidForRecord() {
    val elementName = if (this is GrAnnotation) shortName else text
    holder
      .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.0.not.allowed.for.record", elementName))
      .range(this)
      .withFix(GrChangeModifiersFix(FORBIDDEN_FOR_RECORD, null, GroovyBundle.message("intention.name.remove.wrong.modifiers.for.record"), true))
      .create()
  }

  private fun checkSealingModifiers(owner: GrTypeDefinition) {
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
    val elementName = if (element is GrAnnotation) element.shortName else element.text
    holder
      .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message(
        "inspection.message.only.one.final.sealed.non.sealed.should.be.applied.to.class"))
      .range(element)
      .withFix(GrChangeModifiersFix(CONFLICTING_MODIFIERS, null, GroovyBundle.message("leave.only.modifier.or.annotation.0", elementName)))
      .create()
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
    checkModifierIsNotAllowed(modifierList, GrModifier.SEALED, GroovyBundle.message("inspection.message.modifier.sealed.cannot.be.applied.to.enum.class"), holder)
    checkModifierIsNotAllowed(modifierList, GrModifier.NON_SEALED, GroovyBundle.message("inspection.message.modifier.non.sealed.cannot.be.applied.to.enum.class"), holder)
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
    val fix = GrChangeModifiersFix(CONFLICTING_MODIFIERS, GrModifier.SEALED, GroovyBundle.message("add.modifier.sealed"))
    holder
      .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", name))
      .range(permitsClause.keyword!!).withFix(fix)
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
        .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.not.allowed.in.sealed.hierarchy", ownerType.className))
        .range(element)
        .apply {
          if (baseClass.hasModifierProperty(GrModifier.SEALED)) {
            baseClass.permitsClause?.let { withFix(AddToPermitsList(this@checkPermissions, it)) }
          }
        }
        .create()
    }
  }

  override fun visitSwitchStatement(switchStatement: GrSwitchStatement) {
    visitSwitchElement(switchStatement)
    super.visitSwitchStatement(switchStatement)
  }

  override fun visitSwitchExpression(switchExpression: GrSwitchExpression) {
    visitSwitchElement(switchExpression)
    super.visitSwitchExpression(switchExpression)
  }

  override fun visitMethod(method: GrMethod) {
    if (method.containingClass is GrRecordDefinition && method.isCompactConstructor()) {
      checkCompactConstructor(method)
    }
    super.visitMethod(method)
  }

  private fun checkCompactConstructor(method : GrMethod) {
    if (method.modifierList.getModifier(GrModifier.PUBLIC) == null &&
        method.modifierList.getModifier(GrModifier.PRIVATE) == null &&
        method.modifierList.getModifier(GrModifier.PROTECTED) == null) {
      holder
        .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.compact.constructor.should.have.explicit.visibility.modifier"))
        .range(method.nameIdentifierGroovy)
        .withFix(GrChangeModifiersFix(emptyList(), GrModifier.PUBLIC, GroovyBundle.message("intention.name.make.compact.constructor.public")))
        .create()
    }
  }

  private fun visitSwitchElement(switchElement : GrSwitchElement) {
    if (PsiUtil.isPlainSwitchStatement(switchElement)) {
      // old-style switch statements are handled within the other classes
      return
    }
    val caseSections = switchElement.caseSections ?: emptyArray()
    if (caseSections.isEmpty()) {
      switchElement.firstChild?.let {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.case.or.default.branches.are.expected")).range(it).create()
      }
    }
    checkArrowColonConsistency(caseSections)
    caseSections.forEach(this::visitCaseSection)
    val jointFlow = caseSections.asSequence().flatMap { ControlFlowUtils.getCaseSectionInstructions(it).asSequence() }
    if (!(switchElement is GrSwitchStatement && caseSections.all { it.colon != null }) &&
        caseSections.isNotEmpty() &&
        caseSections.all { it.colon != null } &&
        jointFlow.all { it.element !is GrYieldStatement && it.element !is GrThrowStatement }) {
      val errorOwner = switchElement.firstChild ?: switchElement // try to hang the error on switch keyword
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.yield.or.throw.expected.in.case.section")).range(errorOwner).create()
    }
  }

  private fun checkArrowColonConsistency(caseSections: Array<GrCaseSection>) {
    val arrows = caseSections.mapNotNull(GrCaseSection::getArrow).takeIf(List<*>::isNotEmpty) ?: return
    val colons = caseSections.mapNotNull(GrCaseSection::getColon).takeIf(List<*>::isNotEmpty) ?: return
    for (element in arrows + colons) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.mixing.arrows.colons.not.allowed")).range(element).create()
    }
  }

  override fun visitCaseSection(caseSection: GrCaseSection) {
    if (caseSection.parentOfType<GrSwitchElement>()?.let(PsiUtil::isPlainSwitchStatement) == true) {
      return
    }
    val flow = ControlFlowUtils.getCaseSectionInstructions(caseSection)
    val returns = ControlFlowUtils.collectReturns(flow, caseSection, false)
    for (returnStatement in returns.filterIsInstance<GrReturnStatement>()) {
      holder
        .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.switch.expressions.do.not.support.return"))
        .range(returnStatement)
        .withFix(GrReplaceReturnWithYield())
        .create()
    }
    return super.visitCaseSection(caseSection)
  }
}
