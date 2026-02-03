// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import com.intellij.util.asSafely
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ACTION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.service.resolve.transformation.GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall

/**
 * @author Vladislav.Soroka
 */
class GradleDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    if (expression !is GrClosableBlock) return null
    val file = expression.containingFile
    if (file == null || !FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return null

    var delegatesToInfo = processBeforeContributors(expression)
    if (delegatesToInfo != null) return delegatesToInfo

    for (contributor in GradleMethodContextContributor.EP_NAME.extensions) {
      delegatesToInfo = contributor.getDelegatesToInfo(expression)
      if (delegatesToInfo != null) {
        return delegatesToInfo
      }
    }
    return null
  }

  private fun processBeforeContributors(expression: GrClosableBlock): DelegatesToInfo? {
    val call = getContainingCall(expression) ?: return null
    val resolvedCall = call.advancedResolve() as? GroovyMethodResult ?: return null

    val generatedClosureInfo: DelegatesToInfo? = getDelegateIfGeneratedClosure(resolvedCall)
    if (generatedClosureInfo != null) return generatedClosureInfo

    val candidate = resolvedCall.candidate ?: return null
    val argumentMapping = candidate.argumentMapping ?: return null
    val type = argumentMapping.expectedType(ExpressionArgument(expression)) as? PsiClassType ?: return null
    val clazz = type.resolve() ?: return null
    val delegate = getDelegateFromAction(clazz, type, candidate.method, resolvedCall.substitutor)
                   ?: getDelegateFromClosure(clazz, resolvedCall)
                   ?: return null
    val optionallyWrapped = maybeWrapWithProjectAwareType(delegate, expression, resolvedCall)
    return DelegatesToInfo(optionallyWrapped, Closure.DELEGATE_FIRST)
  }

  /**
   * Allows some NonCodeMembersContributor's like [GradleArtifactHandlerContributor] adding additional resolve logic for PSI elements
   * inside the closable block for which we determine a delegate. For such cases it creates a delegate as [GradleProjectAwareType].
   */
  private fun maybeWrapWithProjectAwareType(delegate: PsiType, expression: GrClosableBlock, resolvedCall: GroovyMethodResult): PsiType {
    val projectAwareReceiver = resolvedCall.candidate?.receiverType
    if (projectAwareReceiver !is GradleProjectAwareType) {
      return delegate
    }
    val fqClassName = delegate.canonicalText
    if (fqClassName != GRADLE_API_ARTIFACT_HANDLER
        && fqClassName != GRADLE_API_PROJECT
    ) {
      return delegate
    }
    val psiClassType = delegate as? PsiClassType
                       ?: createType(fqClassName, expression)
    val result: GradleProjectAwareType = projectAwareReceiver.setType(psiClassType)
    return result
  }

  /**
   * Method could contain delegate in user data if it was generated as overload with Closure
   * @see org.jetbrains.plugins.gradle.service.resolve.transformation.GradleActionToClosureMemberContributor
   */
  private fun getDelegateIfGeneratedClosure(resolvedCall: GroovyMethodResult): DelegatesToInfo? {
    val bridgeDelegate = resolvedCall.element.getUserData(GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY)
    if (bridgeDelegate == null) return null
    val actualBridgeDelegate = resolvedCall.substitutor.substitute(bridgeDelegate)
    return DelegatesToInfo(actualBridgeDelegate, Closure.DELEGATE_FIRST)
  }

  /**
   * Takes a delegate from Action generic parameter.
   * I.e., if [type] is Action<? super T>, a delegate will be a result of substitution in T.
   * It also works for other classes with @HasImplicitReceiver and single generic parameter.
   * But currently only Action has this annotation in Gradle sources.
   */
  private fun getDelegateFromAction(clazz: PsiClass, type: PsiClassType, psiMethod: PsiMethod, substitutor: PsiSubstitutor): PsiType? {
    if (clazz.qualifiedName != GRADLE_API_ACTION
        && !clazz.hasAnnotation("org.gradle.api.HasImplicitReceiver")) {
      return null
    }
    val genericParameter = type.parameters.singleOrNull() ?: return null
    // if generic is super wildcard (i.e. ? super T), take its bound (T)
    val specificType = if (genericParameter is PsiWildcardType && genericParameter.isSuper) {
      genericParameter.bound
    }
    else {
      genericParameter
    }
    val substituted = substitutor.substitute(specificType)
    if (typeParameterIsNotResolved(clazz, psiMethod, substituted)) {
      return null
    }
    return substituted
  }

  /**
   * I.e., returns `true` if method signature looks like `<T> void foo(Action<? super T>)` and nothing meaningful was substituted in the
   * type parameter `T`: [resolvedType] still equals `T` after substitution.
   * */
  private fun typeParameterIsNotResolved(clazz: PsiClass, psiMethod: PsiMethod, resolvedType: PsiType): Boolean {
    // TODO check if there is a better approach than comparison between parameter type name and canonicalText of resolvedType
    val typeParameterNames = clazz.typeParameters
      .plus(psiMethod.typeParameters)
      .map(PsiTypeParameter::getName)
    return typeParameterNames.contains(resolvedType.canonicalText)
  }

  /**
   * If called method has a Closure parameter without @DelegatesTo, searches for overloaded methods with Action to extract a delegate from
   * generic. If Closure parameter has @DelegatesTo, it should be processed by
   * [org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DefaultDelegatesToProvider]
   */
  private fun getDelegateFromClosure(clazz: PsiClass, resolvedCall: GroovyMethodResult): PsiType? {
    if (clazz.qualifiedName != GROOVY_LANG_CLOSURE
        || clazz.hasAnnotation(GROOVY_LANG_DELEGATES_TO)
    ) {
      return null
    }
    val methodName = resolvedCall.candidate?.method?.name ?: return null
    val classProvidingMethod = resolvedCall.candidate?.receiverType.resolve() ?: return null
    val methodOverloads = classProvidingMethod.findMethodsAndTheirSubstitutorsByName(methodName, true)
    for (method in methodOverloads) {
      // TODO maybe skip a method if parameters before Action are not the same as in the resolved method
      val delegate = getDelegateFromMethodSignature(psiMethod = method.first, substitutor = method.second)
      if (delegate != null) return delegate
    }
    return null
  }

  private fun getDelegateFromMethodSignature(psiMethod: PsiMethod, substitutor: PsiSubstitutor): PsiType? {
    val lastParam = psiMethod.parameters.lastOrNull() ?: return null
    val paramType = lastParam.type.asSafely<PsiClassType>() ?: return null
    val resolvedClass = paramType.resolve() ?: return null
    return getDelegateFromAction(resolvedClass, paramType, psiMethod, substitutor)
  }
}
