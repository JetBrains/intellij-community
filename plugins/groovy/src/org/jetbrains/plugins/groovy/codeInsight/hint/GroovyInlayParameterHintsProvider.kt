// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMirrorElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ArgInfo

class GroovyInlayParameterHintsProvider : InlayParameterHintsProvider {

  private companion object {
    val blackList = setOf(
      "org.codehaus.groovy.runtime.DefaultGroovyMethods.*"
    )
  }

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    if (element.containingFile?.virtualFile?.extension == "gradle") return emptyList()
    return (element as? GrCall)?.doGetParameterHints() ?: emptyList()
  }

  private fun GrCall.doGetParameterHints(): List<InlayInfo>? {
    val signature = GrClosureSignatureUtil.createSignature(this) ?: return null
    val infos = GrClosureSignatureUtil.mapParametersToArguments(signature, this) ?: return null
    val original = signature.parameters.zip(infos)
    val closureArgument = closureArguments.singleOrNull()

    // show:
    // - regular literal arguments
    // - varargs which contain literals
    // - prefix unary expressions with numeric literal arguments
    fun shouldShowHint(arg: PsiElement): Boolean {
      if (arg is GrClosableBlock) return true
      if (arg is GrLiteral) return true
      if (arg is GrUnaryExpression) return arg.operand.let { it is GrLiteral && it.value is Number }
      return false
    }

    fun ArgInfo<PsiElement>.shouldShowHint(): Boolean {
      if (args.none(::shouldShowHint)) return false
      if (isMultiArg) return args.none { it is GrNamedArgument } //  do not show named arguments
      if (closureArgument == null) return true
      return closureArgument !in args // do not show closure argument
    }

    // leave only parameters with names
    val filtered = original.mapNotNull {
      val (parameter, info) = it
      val name = parameter.name
      if (name != null && info.shouldShowHint()) name to it.second else null
    }

    return filtered.mapNotNull {
      val (name, info) = it
      info.args.firstOrNull()?.let { arg ->
        val inlayText = if (info.isMultiArg) "...$name" else name
        InlayInfo(inlayText, arg.textRange.startOffset)
      }
    }
  }

  override fun getHintInfo(element: PsiElement): MethodInfo? {
    val call = element as? GrCall
    val resolved = call?.resolveMethod()
    val method = (resolved as? PsiMirrorElement)?.prototype as? PsiMethod ?: resolved
    return method?.getMethodInfo()
  }

  private fun PsiMethod.getMethodInfo(): MethodInfo? {
    val clazzName = containingClass?.qualifiedName ?: return null
    val fullMethodName = StringUtil.getQualifiedName(clazzName, name)
    val paramNames: List<String> = parameterList.parameters.map { it.name ?: "" }
    return MethodInfo(fullMethodName, paramNames, if (language == blackListDependencyLanguage) language else null)
  }

  override fun getDefaultBlackList(): Set<String> = blackList

  override fun getBlackListDependencyLanguage(): JavaLanguage = JavaLanguage.INSTANCE
}