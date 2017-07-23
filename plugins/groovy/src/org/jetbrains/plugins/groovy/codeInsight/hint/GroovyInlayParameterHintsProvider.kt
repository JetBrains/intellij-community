/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    // leave only regular literal arguments and varargs which contain literals
    fun ArgInfo<PsiElement>.shouldShowHint(): Boolean {
      if (args.none { it is GrLiteral || it is GrClosableBlock }) return false // do not show non-literals
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

  override fun getBlackListDependencyLanguage() = JavaLanguage.INSTANCE
}