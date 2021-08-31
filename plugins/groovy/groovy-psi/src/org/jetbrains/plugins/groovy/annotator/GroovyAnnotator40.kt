// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddToPermitsList
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class GroovyAnnotator40(private val holder: AnnotationHolder) : GroovyElementVisitor() {
  override fun visitModifierList(modifierList: GrModifierList) {
    val owner = modifierList.parentsOfType<PsiModifierListOwner>().firstOrNull { it.modifierList === modifierList }
    if (owner is GrTypeDefinition) {
      checkTypeDefinitionModifiers(owner, modifierList)
    }
  }

  private fun createExclusivenessAnnotation(modifier: PsiElement) {
    (modifier.parent as? GrModifierList)?.let {
      checkModifierIsNotAllowed(it, modifier.text, GroovyBundle.message(
        "inspection.message.only.one.final.sealed.non.sealed.should.be.applied.to.class"), holder)
    }
  }

  private fun checkTypeDefinitionModifiers(owner: GrTypeDefinition, modifierList: GrModifierList) {
    var modifierCounter = 0
    val sealed = modifierList.getModifier(GrModifier.SEALED)?.apply { modifierCounter += 1 }
    val nonSealed = modifierList.getModifier(GrModifier.NON_SEALED)?.apply { modifierCounter += 1 }
    val final = modifierList.getModifier(GrModifier.FINAL)?.apply { modifierCounter += 1 }
    if (modifierCounter >= 2) {
      sealed?.let(this::createExclusivenessAnnotation)
      nonSealed?.let(this::createExclusivenessAnnotation)
      final?.let(this::createExclusivenessAnnotation)
    }
    if (checkEnum(owner, sealed, modifierList, nonSealed)) {
      return
    }
    if (sealed != null) {
      if (owner.permitsClause?.keyword == null && (owner.containingFile as? GroovyFile)?.classes?.takeIf { it.isNotEmpty() }?.all { owner.type() !in it.extendsListTypes && owner.type() !in it.implementsListTypes } == true) {
        if (owner.isInterface) {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               GroovyBundle.message("inspection.message.interface.has.no.explicit.or.implicit.implementors",
                                                    owner.name)).range(sealed).create()
        }
        else {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               GroovyBundle.message("inspection.message.class.has.no.explicit.or.implicit.subclasses", owner.name)).range(sealed).create()
        }
      }
    }
  }

  private fun checkEnum(owner: GrTypeDefinition,
                        sealed: PsiElement?,
                        modifierList: GrModifierList,
                        nonSealed: PsiElement?): Boolean {
    if (owner is GrEnumTypeDefinition) {
      sealed?.let {
        checkModifierIsNotAllowed(modifierList, GrModifier.SEALED,
                                  GroovyBundle.message("inspection.message.modifier.sealed.cannot.be.applied.to.enum.class"), holder)
      }
      nonSealed?.let {
        checkModifierIsNotAllowed(modifierList, GrModifier.SEALED,
                                  GroovyBundle.message("inspection.message.modifier.non.sealed.cannot.be.applied.to.enum.class"), holder)
      }
      return true
    }
    else {
      return false
    }
  }

  @Suppress("MoveLambdaOutsideParentheses")
  override fun visitPermitsClause(permitsClause: GrPermitsClause) {
    if (permitsClause.keyword == null || permitsClause.referencedTypes.isEmpty()) {
      return super.visitPermitsClause(permitsClause)
    }
    val owner = permitsClause.parentOfType<GrTypeDefinition>()?.takeIf { it.permitsClause === permitsClause } ?: return
    if (owner.modifierList?.hasModifierProperty(GrModifier.SEALED) != true) {
      val builder = holder.newAnnotation(HighlightSeverity.ERROR,
                           GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", owner.name)).range(permitsClause.keyword!!)
      registerLocalFix(builder, GrModifierFix(owner, GrModifier.SEALED, false, true, { it.startElement.parentOfType<PsiModifierListOwner>()?.modifierList }), permitsClause, GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", owner.name), ProblemHighlightType.ERROR, permitsClause.textRange)
      builder.create()
    }
  }

  override fun visitExtendsClause(extendsClause: GrExtendsClause) {
    checkPermissions(extendsClause)
  }

  override fun visitImplementsClause(implementsClause: GrImplementsClause) {
    checkPermissions(implementsClause)
  }

  private fun checkPermissions(referenceClause: GrReferenceList) {
    val ownerClass = referenceClause
                       .parentOfType<GrTypeDefinition>()
                       ?.takeIf {
                         if (referenceClause is GrExtendsClause)
                           it.extendsClause === referenceClause
                         else
                           it.implementsClause === referenceClause
                       } ?: return
    val ownerType = ownerClass.type()
    for ((type, element) in referenceClause.referencedTypes.zip(referenceClause.referenceElementsGroovy)) {
      val baseClass = type.resolve()?.takeIf { it.hasModifierProperty(GrModifier.SEALED) } as? GrTypeDefinition ?: continue
      if (baseClass.permitsListTypes.isEmpty() && baseClass.containingFile === referenceClause.containingFile) {
        continue
      }
      if (baseClass.permitsListTypes.contains(ownerType)) {
        continue
      }
      holder
        .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.not.allowed.in.sealed.hierarchy", ownerType.className))
        .range(element)
        .apply { baseClass.permitsClause?.let { withFix(AddToPermitsList(ownerClass, it)) } }
        .create()
    }
  }
}
