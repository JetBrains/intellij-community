// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements.annotation

import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR
import org.jetbrains.plugins.groovy.lang.resolve.ast.AffectedMembersCache
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes
import org.jetbrains.plugins.groovy.lang.resolve.ast.getAffectedMembersCache
import org.jetbrains.plugins.groovy.lang.resolve.references.GrMapConstructorPropertyReference

class MapConstructorAttributesFix : SetAnnotationAttributesFix() {

  override fun getNecessaryAttributes(place: PsiElement): Pair<GrAnnotation, Map<String, Any?>>? {
    val namedArgument = place.parentOfType<GrNamedArgument>() ?: return null
    val annotatedClass = GrMapConstructorPropertyReference.getConstructorReference(namedArgument)?.resolveClass()?.element as? GrTypeDefinition ?: return null
    val mapConstructorAnno = annotatedClass.getAnnotation(GROOVY_TRANSFORM_MAP_CONSTRUCTOR) as? GrAnnotation ?: return null
    val affectedIdentifiers: Set<String> =
      getAffectedMembersCache(mapConstructorAnno).getAffectedMembers().mapNotNullTo(LinkedHashSet(), AffectedMembersCache.Companion::getExternalName)
    val collector = mutableMapOf<String, Any?>()
    val labels = run {
      val namedArgOwner = place.parentOfType<GrNamedArgumentsOwner>() ?: return null
      namedArgOwner.namedArguments.mapNotNull { it?.label }
    }
    for (label in labels) {
      if (affectedIdentifiers.contains(label.name)) continue
      processLabel(collector, label, annotatedClass)
    }
    return mapConstructorAnno to collector
  }

  companion object {
    private fun processLabel(collector: MutableMap<String, Any?>,
                             label: GrArgumentLabel,
                             annotationOwner: GrTypeDefinition): Unit = with(TupleConstructorAttributes) {
      val name = label.name ?: return
      val resolved = label.constructorPropertyReference?.resolve() ?: return
      if (AffectedMembersCache.isInternal(name)) collector[ALL_NAMES] = true
      if (resolved is PsiModifierListOwner && resolved.hasModifierProperty(PsiModifier.STATIC)) {
        collector["includeStatic"] = true
      }
      if (resolved !is PsiMember) return
      if (resolved.containingClass == annotationOwner) {
        when {
          resolved is GrField && resolved.isProperty -> collector[INCLUDE_PROPERTIES] = true
          resolved is GrField && !resolved.isProperty -> collector[INCLUDE_FIELDS] = true
          resolved is GrMethod -> collector[ALL_PROPERTIES] = true
        }
      }
      else {
        when {
          resolved is GrField && resolved.isProperty -> collector[INCLUDE_SUPER_PROPERTIES] = true
          resolved is GrField && !resolved.isProperty -> collector[INCLUDE_SUPER_FIELDS] = true
          resolved is PsiField -> collector[INCLUDE_SUPER_FIELDS] = true // java class handling
        }
      }
    }
  }

  override fun getName(): String {
    return GroovyBundle.message("intention.name.add.required.attributes.to.map.constructor")
  }
}
