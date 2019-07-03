// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class ParametrizedClosure(val parameter: GrParameter) {
  val types: MutableList<PsiType> = ArrayList()
  val typeParameters: MutableList<PsiTypeParameter> = ArrayList()

  companion object {
    fun ensureImports(elementFactory: GroovyPsiElementFactory, file: GroovyFile) =
      arrayOf(CLOSURE_PARAMS_FQ, FROM_STRING_FQ)
        .map { elementFactory.createImportStatementFromText(it, false, false, null) }
        .filter { it.importedName !in file.importStatements.map { importStatement -> importStatement.importedName } }
        .forEach { file.addImport(it) }

    private const val CLOSURE_PARAMS = "ClosureParams"
    private const val FROM_STRING = "FromString"
    private const val CLOSURE_PARAMS_FQ: String = "groovy.transform.stc.$CLOSURE_PARAMS"
    private const val FROM_STRING_FQ = "groovy.transform.stc.$FROM_STRING"
  }

  override fun toString(): String =
    "${typeParameters.joinToString(prefix = "<", postfix = ">") { it.text }} Closure ${types.joinToString(prefix = "(", postfix = ")")}"


  fun renderTypes(elementFactory: GroovyPsiElementFactory) {
    parameter.modifierList.addAnnotation("$CLOSURE_PARAMS(value=$FROM_STRING, options=[\"${types.joinToString(",") { tryToExtractUnqualifiedName(it.canonicalText) }}\"])")
    //parameter.addBefore(elementFactory.createAnnotationFromText(
    //  "@$CLOSURE_PARAMS_FQ(value=$FROM_STRING_FQ, options=[\"${types.joinToString(",") { tryToExtractUnqualifiedName(it.canonicalText) }}\"])"),
    //                    parameter.firstChild)
  }

  fun substituteTypes(resultSubstitutor: PsiSubstitutor) {
    val substitutedTypes = types.map { resultSubstitutor.substitute(it) }
    types.clear()
    types.addAll(substitutedTypes)
  }

}