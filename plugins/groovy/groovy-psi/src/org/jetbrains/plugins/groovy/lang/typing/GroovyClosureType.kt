// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.containers.minimalElements
import com.intellij.util.recursionSafeLazy
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.compare
import org.jetbrains.plugins.groovy.lang.resolve.impl.filterApplicable

abstract class GroovyClosureType(
  private val myContext: PsiElement
) : GrLiteralClassType(LanguageLevel.JDK_1_5, myContext) {

  override fun isValid(): Boolean = myContext.isValid
  final override fun getJavaClassName(): String = GROOVY_LANG_CLOSURE
  final override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType = error("must not be called")
  @NonNls final override fun toString(): String = "Closure"

  final override fun getParameters(): Array<out PsiType?> = myTypeArguments ?: PsiType.EMPTY_ARRAY

  private val myTypeArguments: Array<out PsiType?>? by recursionSafeLazy {
    val closureClazz = resolve()
    if (closureClazz == null || closureClazz.typeParameters.size != 1) {
      return@recursionSafeLazy PsiType.EMPTY_ARRAY
    }
    val type: PsiType? = returnType(null)
    if (type == null || type === PsiType.NULL) {
      arrayOf<PsiType?>(null)
    }
    else {
      arrayOf(TypesUtil.boxPrimitiveType(type, psiManager, resolveScope, true))
    }
  }

  abstract val signatures: List<@JvmWildcard CallSignature<*>>

  open fun curry(position: Int, arguments: Arguments, context: PsiElement): PsiType {
    return GroovyCurriedClosureType(this, position, arguments, 0, context)
  }

  open fun returnType(arguments: Arguments?): PsiType? {
    val returnTypes = applicableSignatures(arguments).map {
      it.returnType
    }
    return when (returnTypes.size) {
      0 -> null
      1 -> returnTypes[0]
      else -> TypesUtil.getLeastUpperBoundNullable(returnTypes, myContext.manager)
    }
  }

  fun applicableSignatures(arguments: Arguments?): Collection<CallSignature<*>> {
    return if (arguments == null) {
      signatures
    }
    else {
      doApplyTo(arguments).map {
        it.first
      }
    }
  }

  fun applyTo(arguments: Arguments): Collection<ArgumentMapping<*>> {
    return doApplyTo(arguments).map {
      it.second
    }
  }

  private fun doApplyTo(arguments: Arguments): Collection<SignatureMapping> {
    val allMappings: List<SignatureMapping> = signatures.mapNotNull {
      it.applyTo(arguments, myContext)?.let { mapping ->
        SignatureMapping(it, mapping)
      }
    }
    val (applicable, canChooseOverload) = allMappings.filterApplicable { (_, mapping) ->
      mapping.applicability()
    }
    if (applicable.isEmpty()) {
      return allMappings
    }
    return if (canChooseOverload) {
      applicable.minimalElements(comparator)
    }
    else {
      applicable
    }
  }
}

private typealias SignatureMapping = Pair<CallSignature<*>, ArgumentMapping<*>>

private val comparator: Comparator<SignatureMapping> = Comparator.comparing(
  { it.second },
  { left: ArgumentMapping<*>, right: ArgumentMapping<*> -> compare(left, right) }
)
