// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.*
import com.intellij.util.asSafely
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.transformation.GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
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
    var delegatesToInfo = processBeforeContributors(expression)
    if (delegatesToInfo != null) return delegatesToInfo

    val file = expression.containingFile
    if (file == null || !FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return null

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

    val argumentMapping = resolvedCall.candidate?.argumentMapping ?: return null
    val type = argumentMapping.expectedType(ExpressionArgument(expression)) as? PsiClassType ?: return null
    val clazz = type.resolve() ?: return null
    if (clazz.providesDelegateInGeneric()) {
      return getDelegateInfoFromGenericType(type, resolvedCall.substitutor)
    }
    else if (clazz.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      return getDelegateInfoFromClosure(resolvedCall)
    }
    return null
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

  private fun PsiClass.providesDelegateInGeneric(): Boolean {
    return qualifiedName == GradleCommonClassNames.GRADLE_API_ACTION
           || hasAnnotation("org.gradle.api.HasImplicitReceiver")
  }

  /**
   * I.e., if [type] is Action<? super T>, a delegate will be a result of substitution in T
   */
  private fun getDelegateInfoFromGenericType(type: PsiClassType, substitutor: PsiSubstitutor): DelegatesToInfo? {
    val typeArgument = type.parameters.singleOrNull() ?: return null
    val genericType = if (typeArgument is PsiWildcardType && typeArgument.isSuper) {
      typeArgument.bound
    }
    else {
      typeArgument
    }
    val delegateType = substitutor.substitute(genericType)
    return DelegatesToInfo(delegateType, Closure.DELEGATE_FIRST)
  }

  private fun getDelegateInfoFromClosure(resolvedCall: GroovyMethodResult): DelegatesToInfo? {
    val methodName = resolvedCall.candidate?.method?.name ?: return null
    val classProvidingMethod = resolvedCall.candidate?.receiverType.resolve() ?: return null
    val methodOverloads = classProvidingMethod.findMethodsAndTheirSubstitutorsByName(methodName, true)
    for (method in methodOverloads) {
      val delegatesToInfo = getDelegateFromMethodSignature(psiMethod = method.first, substitutor = method.second)
      if (delegatesToInfo != null) return delegatesToInfo
    }
    return null
  }

  private fun getDelegateFromMethodSignature(psiMethod: PsiMethod, substitutor: PsiSubstitutor): DelegatesToInfo? {
    val lastParam = psiMethod.parameters.lastOrNull() ?: return null
    val paramType = lastParam.type.asSafely<PsiClassType>() ?: return null
    val resolveResult = paramType.resolveGenerics()
    val resolvedClass = resolveResult.element ?: return null
    if (resolvedClass.providesDelegateInGeneric()) {
      return getDelegateInfoFromGenericType(paramType, substitutor)
    }
    return null
  }
}
