// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil.findFirstParent
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.util.parents
import com.intellij.util.containers.toArray
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

internal fun getScriptDeclarations(fileImpl: GroovyFileImpl, topLevelOnly: Boolean): Array<out GrVariableDeclaration> {
  val tree = fileImpl.stubTree ?: return fileImpl.collectScriptDeclarations(topLevelOnly)
  return if (topLevelOnly) {
    val root: StubElement<*> = tree.root
    root.getChildrenByType(GroovyElementTypes.VARIABLE_DECLARATION, GrVariableDeclaration.EMPTY_ARRAY)
  }
  else {
    tree.plainList.filter {
      it.stubType === GroovyElementTypes.VARIABLE_DECLARATION
    }.map {
      it.psi as GrVariableDeclaration
    }.toTypedArray()
  }
}

private val scriptBodyDeclarationsKey = Key.create<CachedValue<Array<GrVariableDeclaration>>>("groovy.script.declarations.body")
private val scriptDeclarationsKey = Key.create<CachedValue<Array<GrVariableDeclaration>>>("groovy.script.declarations.all")

private fun GroovyFileImpl.collectScriptDeclarations(topLevelOnly: Boolean): Array<GrVariableDeclaration> {
  val key = if (topLevelOnly) scriptBodyDeclarationsKey else scriptDeclarationsKey
  val provider = {
    Result.create(doCollectScriptDeclarations(topLevelOnly), this)
  }
  return CachedValuesManager.getManager(project).getCachedValue(this, key, provider, false)
}

private fun GroovyFileImpl.doCollectScriptDeclarations(topLevelOnly: Boolean): Array<GrVariableDeclaration> {
  val result = mutableListOf<GrVariableDeclaration>()
  accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element is GrVariableDeclaration && element.modifierList.rawAnnotations.isNotEmpty()) {
        result.add(element)
      }
      if (element is GrTypeDefinition) return //  do not go into classes
      if (element is GrMethod && topLevelOnly) return // do not go into methods if top level only
      super.visitElement(element)
    }
  })
  return result.toTypedArray()
}

fun GrCodeReferenceElement.doGetKind(): CodeReferenceKind {
  return when (val parent = parent) {
    is GrPackageDefinition -> CodeReferenceKind.PACKAGE_REFERENCE
    is GrImportStatement -> CodeReferenceKind.IMPORT_REFERENCE
    is GrCodeReferenceElement -> parent.kind
    else -> CodeReferenceKind.REFERENCE
  }
}

fun getQualifiedReferenceName(reference: GrReferenceElement<*>): String? {
  val parts = mutableListOf<String>()
  var current = reference
  while (true) {
    val name = current.referenceName ?: return null
    parts.add(name)
    val qualifier = current.qualifier ?: break
    if (qualifier !is GrReferenceElement<*>) return null
    current = qualifier
  }
  return parts.reversed().joinToString(separator = ".")
}

fun GrMethodCall.isImplicitCall(): Boolean {
  val expression = invokedExpression
  return expression !is GrReferenceExpression || expression.isImplicitCallReceiver
}

fun GrMethodCall.isExplicitCall(): Boolean {
  val expression = invokedExpression
  return expression is GrReferenceExpression && !expression.isImplicitCallReceiver && expression.referenceName != null
}

fun GrCodeReferenceElement.getDiamondTypes(): Array<out PsiType?> {
  val result = advancedResolve()
  return result.getTypeArgumentsFromResult()
}

fun GroovyResolveResult.getTypeArgumentsFromResult(): Array<out PsiType?> {
  val clazz = element as? PsiClass ?: return PsiType.EMPTY_ARRAY
  val substitutor = substitutor // this may start inference session
  return clazz.typeParameters.map(substitutor::substitute).toArray(PsiType.EMPTY_ARRAY)
}

/**
 * @return array of type arguments:
 * <ul>
 * <li>{@code null} means there are no type arguments and result should be raw;</li>
 * <li>empty array means the reference is a diamond and type arguments should be inferred from the context;</li>
 * <li>non-empty array means the reference has explicit type arguments and they should be used in substitution.</li>
 * </ul>
 */
val GrCodeReferenceElement.explicitTypeArguments: Array<out PsiType>?
  get() = if (shouldInferTypeArguments()) {
    PsiType.EMPTY_ARRAY
  }
  else {
    typeArgumentList?.typeArguments
  }

fun GrCodeReferenceElement.shouldInferTypeArguments(): Boolean {
  val typeArgumentList = typeArgumentList
  return when {
    typeArgumentList == null -> isInClosureSafeCast() // treat `Function` in `{} as Function` as a diamond
    typeArgumentList.isDiamond -> isUsageInCodeBlock() // diamonds in parameter lists are not inferrable
    else -> false // explicit type arguments
  }
}

private fun GrCodeReferenceElement.isUsageInCodeBlock(): Boolean {
  return parents(false)
    .takeWhile { it !is GrControlFlowOwner }
    .all { it !is GrParameter }
}

/**
 * @return `true` if this reference is in type element of a safe cast with a closure operand, e.g. `{} as Foo`
 */
private fun GrCodeReferenceElement.isInClosureSafeCast(): Boolean {
  val typeElement = parent as? GrClassTypeElement
  val safeCast = typeElement?.parent as? GrSafeCastExpression
  return safeCast?.operand is GrClosableBlock
}

/**
 * @return `true` if variable is declared in given block(nested closure and method blocks excluded)
 */
fun GrVariable.isDeclaredIn(block: GrControlFlowOwner): Boolean {
  if (this is GrParameter && this.parent !is GrForInClause) {
    val parametersOwner = getParentOfType(block, GrParametersOwner::class.java, false)
    return declarationScope == parametersOwner
  }

  val parent = findFirstParent(this) { block == it || it is GrMethod || it is GrClosableBlock }

  return parent == block
}

fun isThisRef(expression: GrExpression?): Boolean {
  return expression is GrReferenceExpression &&
         expression.qualifier == null &&
         JavaKeywords.THIS == expression.referenceName
}
