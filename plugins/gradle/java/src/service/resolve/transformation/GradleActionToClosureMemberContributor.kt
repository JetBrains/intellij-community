// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.transformation

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.impl.light.LightTypeParameter
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

/**
 * For each method accepting [org.gradle.api.Action], Gradle generates a similar method but accepting a closure.
 * It might seem like a redundant generation: Groovy supports SAM-coercions, so the method with Action will be safely chosen.
 * Unfortunately, we have Kotlin, which has its own view on resolution priority. There are classes which have
 * overloads to Action and kotlin.Function1 (isomorphic to Action), and for Groovy these two classes are the same.
 * But Closure has the highest overload priority for Groovy, so to avoid an error about ambiguity, we have to follow the logic of Gradle.
 * @see org.gradle.internal.instantiation.generator.AbstractClassGenerator
 */
class GradleActionToClosureMemberContributor : NonCodeMembersContributor() {
  override fun getClassNames(): Collection<String> = emptyList()

  override fun getParentClassName(): String? = null

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {

    if (aClass == null) {
      return
    }
    if (place.containingFile.isGradleFile().not()) {
      return
    }
    if (!processor.shouldProcessMethods()) {
      return
    }
    val nameHint = processor.getName(state) ?: ""
    val methodRegistry = aClass.allMethodsAndTheirSubstitutors.groupBy { it.first.name }

    for ((groupName, overloads) in methodRegistry) {
      if (nameHint !in groupName) {
        continue
      }
      for (method in overloads) {
        processActionOverrider(method.first, method.second, processor, state)
      }
    }
  }
}

private fun processActionOverrider(method : PsiMethod, substitutor: PsiSubstitutor, processor: PsiScopeProcessor, state: ResolveState) {
  val parameters = method.parameterList.parameters
  val lastParameter = parameters.lastOrNull() ?: return
  val type = lastParameter.type.asSafely<PsiClassType>() ?: return
  val resolveResult = type.resolveGenerics()
  val resolvedClass = resolveResult.element ?: return
  if (resolvedClass.qualifiedName != GradleCommonClassNames.GRADLE_API_ACTION) return
  val delegate = type.parameters.singleOrNull()?.unwrapWildcard() ?: return
  val overrider = createMethodWithClosure(method, lastParameter, substitutor, delegate)
  processor.execute(overrider, state)
}

private fun createMethodWithClosure(
  methodWithAction: PsiMethod,
  parameterWithAction: PsiParameter,
  substitutor: PsiSubstitutor,
  delegate: PsiType,
): PsiMethod {
  val newMethod = GrLightMethodBuilder(methodWithAction.manager, methodWithAction.name)
  val newMapping = mutableMapOf<PsiTypeParameter, PsiType>()
  methodWithAction.typeParameters.forEach { typeParameter ->
    val newTypeParameter = object : LightTypeParameter(typeParameter) {
      override fun getExtendsList(): PsiReferenceList {
        val original = super.getExtendsList()
        return object : LightReferenceListBuilder(original.manager, original.role) {
          override fun getReferencedTypes(): Array<PsiClassType> {
            val types = original.referencedTypes.map { substitutor.substitute(it) as PsiClassType }
            return if (types.isEmpty()) PsiClassType.EMPTY_ARRAY else types.toTypedArray()
          }
        }
      }
    }
    newMapping[typeParameter] = newTypeParameter.type()
    newMethod.addTypeParameter(newTypeParameter)
  }
  val finalSubstitutor = substitutor.putAll(newMapping)

  val parameters = methodWithAction.parameterList.parameters
  val coreParameters = parameters.take(parameters.size - 1)
  for (parameter in coreParameters) {
    newMethod.addParameter(parameter.name, finalSubstitutor.substitute(parameter.type))
  }

  val closure = PsiType.getTypeByName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, methodWithAction.project, methodWithAction.resolveScope)
  val closureParameter = GrLightParameter(parameterWithAction.name, closure, parameterWithAction)
  newMethod.putUserData(GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY, finalSubstitutor.substitute(delegate))
  newMethod.addParameter(closureParameter)
  newMethod.navigationElement = methodWithAction
  newMethod.containingClass = methodWithAction.containingClass
  newMethod.originInfo = "Generated by decoration of Gradle Action-ending method"
  newMethod.returnType = finalSubstitutor.substitute(methodWithAction.returnType)
  newMethod.isDeprecated = methodWithAction.isDeprecated
  return newMethod
}

private fun PsiType.unwrapWildcard() : PsiType = if (this is PsiWildcardType) {
  this.bound ?: PsiType.getTypeByName(CommonClassNames.JAVA_LANG_OBJECT, this.manager.project, this.resolveScope)
} else {
  this
}

internal val GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY : Key<PsiType> = Key.create("Delegate for Gradle generated closure overload")
