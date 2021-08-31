// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.GroovyBundle
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
    holder.newAnnotation(HighlightSeverity.ERROR,
                         GroovyBundle.message("inspection.message.only.one.final.sealed.non.sealed.should.be.applied.to.class")).range(
      modifier).create()

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
    if (owner is GrEnumTypeDefinition) {
      sealed?.let {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.modifier.sealed.cannot.be.applied.to.enum.class")).range(it).create()
      }
      nonSealed?.let {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.modifier.non.sealed.cannot.be.applied.to.enum.class")).range(it).create()
      }
      return
    }
    if (sealed != null) {
      if (owner.permitsClause?.keyword == null && (owner.containingFile as? GroovyFile)?.classes?.takeIf { it.isNotEmpty() }?.all { owner.type() !in it.extendsListTypes && owner.type() !in it.implementsListTypes } == true) {
        if (owner.isInterface) {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               GroovyBundle.message("inspection.message.interface.has.no.explicit.or.implicit.implementors",
                                                    owner.name)).range(sealed).create()
        } else {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               GroovyBundle.message("inspection.message.class.has.no.explicit.or.implicit.subclasses", owner.name)).range(sealed).create()
        }
      }
    }
  }

  override fun visitPermitsClause(permitsClause: GrPermitsClause) {
    if (permitsClause.keyword == null || permitsClause.referencedTypes.isEmpty()) {
      return super.visitPermitsClause(permitsClause)
    }
    val owner = permitsClause.parentOfType<GrTypeDefinition>()?.takeIf { it.permitsClause === permitsClause } ?: return
    if (owner.modifierList?.hasModifierProperty(GrModifier.SEALED) != true) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           GroovyBundle.message("inspection.message.invalid.permits.clause.must.be.sealed", owner.name)).range(
        permitsClause.keyword!!).create()
    }
    val ownerType = owner.type()
    for (subclass in permitsClause.referenceElementsGroovy) {
      val classDefinition = subclass.resolve() as? GrTypeDefinition ?: continue
      val targetReferenceList = when {
        owner.isInterface && classDefinition.isInterface -> classDefinition.extendsListTypes
        owner.isInterface -> classDefinition.implementsListTypes
        else -> classDefinition.extendsListTypes
      }
      if (ownerType !in targetReferenceList) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             GroovyBundle.message("inspection.message.invalid.permits.clause.must.directly.extend", classDefinition.name,
                                                  owner.name)).range(subclass).create()
      }
    }
  }

  override fun visitExtendsClause(extendsClause: GrExtendsClause) {
    checkPermissions(extendsClause)
  }

  private fun checkPermissions(extendsClause: GrReferenceList) {
    val ownerType = extendsClause.parentOfType<GrTypeDefinition>()?.takeIf { it.extendsClause === extendsClause }?.type() ?: return
    for ((type, element) in extendsClause.referencedTypes.zip(extendsClause.referenceElementsGroovy)) {
      val clazz = type.resolve()?.takeIf { it.hasModifierProperty(GrModifier.SEALED) } ?: continue
      if (clazz.permitsListTypes.isEmpty() && clazz.containingFile === extendsClause.containingFile) {
        continue
      }
      if (clazz.permitsListTypes.contains(ownerType)) {
        continue
      }
      holder.newAnnotation(HighlightSeverity.ERROR,
                           GroovyBundle.message("inspection.message.not.allowed.in.sealed.hierarchy", ownerType.className)).range(
        element).create()
    }
  }

  override fun visitImplementsClause(implementsClause: GrImplementsClause) {
    checkPermissions(implementsClause)
  }
}
