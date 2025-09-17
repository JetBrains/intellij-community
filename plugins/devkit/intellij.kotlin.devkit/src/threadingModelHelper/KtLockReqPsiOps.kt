
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class KtLockReqPsiOps : LockReqPsiOps {

  override fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    val ktFunction = method as? KtNamedFunction ?: return emptyList()
    val callees = mutableListOf<PsiMethod>()

    ktFunction.collectDescendantsOfType<KtCallExpression>().forEach { call ->
      resolveCallToFunction(call)?.let { resolvedFunction ->
        if (resolvedFunction is PsiMethod) {
          callees.add(resolvedFunction)
        }
      }
    }
    return callees
  }

  fun canBeOverridden(method: PsiMethod): Boolean {
    val ktFunction = method as? KtNamedFunction ?: return false
    return !ktFunction.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
           !ktFunction.hasModifier(KtTokens.FINAL_KEYWORD) &&
           ktFunction.containingClassOrObject?.hasModifier(KtTokens.OPEN_KEYWORD) == true
  }

  override fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImplementations: Int): List<PsiMethod> {
    val ktFunction = method as? KtNamedFunction ?: return emptyList()
    val inheritors = mutableListOf<PsiMethod>()

    analyze(ktFunction) {
      val functionSymbol = ktFunction.symbol as? KaNamedFunctionSymbol ?: return emptyList()
      val overridingSymbols = functionSymbol.allOverriddenSymbols

      overridingSymbols.take(maxImplementations).forEach { symbol ->
        val psi = symbol.psi
        if (psi is PsiMethod) {
          inheritors.add(psi)
        }
      }
    }
    return inheritors
  }

  override fun findImplementations(topicClass: PsiClass, scope: GlobalSearchScope, maxImplementations: Int): List<PsiClass> {
    val ktClass = topicClass as? KtClass ?: return emptyList()
    val implementations = mutableListOf<PsiClass>()

    analyze(ktClass) {
      val classSymbol = ktClass.symbol as? KaNamedClassSymbol ?: return emptyList()

    }
    return implementations
  }

  override fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean {
    TODO("Not yet implemented")
  }

  fun inheritsFromAny(ktClassOrObject: KtClassOrObject, baseClassNames: Collection<String>): Boolean {
    analyze(ktClassOrObject) {
      val classSymbol = ktClassOrObject.symbol as? KaClassSymbol ?: return false
      val allSuperTypes = classSymbol.superTypes

      return allSuperTypes.any { superType ->
        val className = (superType as? KaClassType)?.classId?.asFqNameString()
        className in baseClassNames
      }
    }
  }

  override fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean {
    return packagePrefixes.any { prefix -> className.startsWith(prefix) }
  }

  override fun resolveReturnType(method: PsiMethod): PsiClass? {
    TODO("Not yet implemented")
  }

  override fun extractTypeArguments(type: PsiType): List<PsiType> {
    TODO("Not yet implemented")
  }

  fun resolveReturnType(call: KtCallExpression): KtClass? {
    analyze(call) {
      val callSymbol = call.resolveToCall()?.successfulFunctionCallOrNull()
      //val returnType = callSymbol?.symbol?.returnType as? KaClassType
      //return returnType?.classId as? KtClass
    }
  }

  fun getReceiverType(call: KtCallExpression): String? {
    val receiverExpression = call.calleeExpression?.let { callee ->
      when (callee) {
        is KtDotQualifiedExpression -> callee.receiverExpression
        else -> null
      }
    }

    return when (receiverExpression) {
      is KtNameReferenceExpression -> {
        analyze(receiverExpression) {
          val symbol = receiverExpression.mainReference.resolveToSymbol()
          when (symbol) {
            is KaVariableSymbol -> (symbol.returnType as? KaClassType)?.classId?.asFqNameString()
            is KaClassSymbol -> symbol.classId?.asFqNameString()
            else -> null
          }
        }
      }
      else -> null
    }
  }

  private fun resolveCallToFunction(call: KtCallExpression): KtNamedFunction? {
    analyze(call) {
      val callSymbol = call.resolveToCall()?.successfulFunctionCallOrNull()
      return null
    }
  }
}