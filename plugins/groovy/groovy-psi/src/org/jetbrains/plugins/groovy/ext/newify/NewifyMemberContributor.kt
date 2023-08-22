// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parents
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.getClassArrayValue
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyImportHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.processUnqualified
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassProcessor
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import java.util.regex.PatternSyntaxException

@NlsSafe internal const val newifyAnnotationFqn = "groovy.lang.Newify"
@NonNls
internal const val newifyOriginInfo = "by @Newify"

interface GrNewifyAttributes {
  companion object {
    @NlsSafe
    const val VALUE = "value"
    @NlsSafe
    const val AUTO = "auto"
    @NlsSafe
    const val NEW = "new"
  }
}

internal class NewifyMemberContributor : NonCodeMembersContributor() {
  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!processor.shouldProcessMethods()) return
    if (place !is GrReferenceExpression) return
    val referenceName = place.referenceName ?: return
    val newifyAnnotations = getNewifyAnnotations(place)
    if (newifyAnnotations.isEmpty()) return

    val qualifier = place.qualifierExpression
    val type = (qualifier as? GrReferenceExpression)?.resolve() as? PsiClass

    if (qualifier == null && newifyAnnotations.any { matchesPattern(referenceName, it) }) {
       if (!processByName(referenceName, place, processor, state)) {
         return
       }
    }

    for (annotation in newifyAnnotations) {

      if (qualifier == null) {
        val newifiedClasses = getClassArrayValue(annotation, GrNewifyAttributes.VALUE, true)
        newifiedClasses
          .filter { psiClass -> GrStaticChecker.isStaticsOK(psiClass, place, psiClass, false) }
          .flatMap { buildConstructors(it, it.name) }
          .forEach { ResolveUtil.processElement(processor, it, state) }
      }
      val createNewMethods = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, GrNewifyAttributes.AUTO)
      if (type != null && createNewMethods) {
          buildConstructors(type, GrNewifyAttributes.NEW).forEach {
            ResolveUtil.processElement(processor, it, state)
        }
      }
    }
  }

  private fun processByName(referenceName: String,
                            place: PsiElement,
                            processor: PsiScopeProcessor,
                            state: ResolveState): Boolean {
    val classProcessor = ClassProcessor(referenceName, place)
    place.processUnqualified(classProcessor, ResolveState.initial())

    for (result in classProcessor.results) {
      val clazz = result.element as? PsiClass
      if (clazz == null || !GrStaticChecker.isStaticsOK(clazz, place, clazz, false)) {
        continue
      }
      val newState: ResolveState = produceStateWithContext(result, place, referenceName, state)
      val newifiedConstructors: List<NewifiedConstructor> = buildConstructors(clazz, clazz.name)
      for (newifiedConstructor in newifiedConstructors) {
        if (!ResolveUtil.processElement(processor, newifiedConstructor, newState)) {
          return false
        }
      }
    }
    return true
  }

  private fun produceStateWithContext(result: GroovyResolveResult,
                                      place: PsiElement,
                                      referenceName: String,
                                      state: ResolveState): ResolveState {
    val import = result.currentFileResolveContext as? GrImportStatement ?: return state
    val containingFile = place.takeIf(PsiElement::isValid)?.containingFile as? GroovyFile ?: return state
    if (GroovyImportHelper.isImplicitlyImported(result.element, referenceName, containingFile)) {
      return state
    }
    return state.put(RESOLVE_CONTEXT, import)
  }

  companion object {
    @JvmStatic
    fun getNewifyAnnotations(element: PsiElement): List<PsiAnnotation> = element.parents(true).flatMap {
      val owner = it as? PsiModifierListOwner
      val seq = owner?.modifierList?.annotations?.asSequence()?.filter { it.qualifiedName == newifyAnnotationFqn }
      return@flatMap seq ?: emptySequence()
    }.toList()

    @JvmStatic
    fun matchesPattern(name: String, annotation: PsiAnnotation): Boolean {
      val regex = try {
        val pattern = GrAnnotationUtil.inferStringAttribute(annotation, "pattern") ?: return false
        Regex(pattern)
      }
      catch (e: PatternSyntaxException) {
        return false
      }
      return regex matches name
    }
  }

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

  class NewifiedConstructor(myManager: PsiManager, newName: String) : LightMethodBuilder(myManager, GroovyLanguage, newName) {
    init {
      addModifier(PsiModifier.STATIC)
      isConstructor = true
      originInfo = newifyOriginInfo
    }

  }
}