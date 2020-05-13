// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parentsWithSelf
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.getClassArrayValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.processUnqualified
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassProcessor
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import java.util.regex.PatternSyntaxException

internal const val newifyAnnotationFqn = "groovy.lang.Newify"
internal const val newifyOriginInfo = "by @Newify"

class NewifyMemberContributor : NonCodeMembersContributor() {
  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!processor.shouldProcessMethods()) return
    if (place !is GrReferenceExpression) return
    val referenceName = place.referenceName ?: return
    val newifyAnnotations = place.listNewifyAnnotations()
    if (newifyAnnotations.isEmpty()) return

    val qualifier = place.qualifierExpression
    val type = (qualifier as? GrReferenceExpression)?.resolve() as? PsiClass

    for (annotation in newifyAnnotations) {

      if (qualifier == null) {
        val patternClasses: List<PsiClass> = getClassesForPatternAttribute(annotation, referenceName, place)
        val newifiedClasses = getClassArrayValue(annotation, "value", true)
        (patternClasses + newifiedClasses)
          .filter { psiClass -> GrStaticChecker.isStaticsOK(psiClass, place, psiClass, false) }
          .flatMap { buildConstructors(it, it.name) }
          .forEach { ResolveUtil.processElement(processor, it, state) }
      }

      val createNewMethods = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "auto")
      if (type != null && createNewMethods) {
          buildConstructors(type, "new").forEach {
            ResolveUtil.processElement(processor, it, state)
        }
      }
    }
  }

  private fun getClassesForPatternAttribute(annotation: PsiAnnotation, referenceName: String, place: PsiElement): List<PsiClass> {
    val regex = try {
      val pattern = GrAnnotationUtil.inferStringAttribute(annotation, "pattern") ?: return emptyList()
      Regex(pattern)
    }
    catch (e: PatternSyntaxException) {
      return emptyList()
    }
    return if (regex matches referenceName) {
      val classProcessor = ClassProcessor(referenceName, place)
      place.processUnqualified(classProcessor, ResolveState.initial())
      classProcessor.results.mapNotNull(GroovyResolveResult::getElement).filterIsInstance<PsiClass>()
    }
    else {
      emptyList()
    }
  }

  private fun PsiElement.listNewifyAnnotations() = parentsWithSelf.flatMap {
    val owner = it as? PsiModifierListOwner
    val seq = owner?.modifierList?.annotations?.asSequence()?.filter { it.qualifiedName == newifyAnnotationFqn }
    return@flatMap seq ?: emptySequence()
  }.toList()

  private fun buildConstructors(clazz: PsiClass, newName: String?): List<NewifiedConstructor> {
    newName ?: return emptyList()
    val constructors = clazz.constructors
    if (constructors.isNotEmpty()) {
      return constructors.mapNotNull { buildNewifiedConstructor(it, newName) }
    }
    else {
      return listOf(buildNewifiedConstructor(clazz, newName))
    }
  }

  private fun buildNewifiedConstructor(myPrototype: PsiMethod, newName: String): NewifiedConstructor? {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    val psiClass = myPrototype.containingClass ?: return null
    builder.containingClass = psiClass
    builder.setMethodReturnType(TypesUtil.createType(psiClass))
    builder.navigationElement = myPrototype
    myPrototype.parameterList.parameters.forEach {
      builder.addParameter(it)
    }
    myPrototype.throwsList.referencedTypes.forEach {
      builder.addException(it)
    }
    myPrototype.typeParameters.forEach {
      builder.addTypeParameter(it)
    }
    return builder
  }

  private fun buildNewifiedConstructor(myPrototype: PsiClass, newName: String): NewifiedConstructor {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    builder.containingClass = myPrototype
    builder.setMethodReturnType(TypesUtil.createType(myPrototype))
    builder.navigationElement = myPrototype
    return builder
  }

  class NewifiedConstructor(val myManager: PsiManager, val newName: String) : LightMethodBuilder(myManager, newName) {
    init {
      addModifier(PsiModifier.STATIC)
      originInfo = newifyOriginInfo
    }

  }
}