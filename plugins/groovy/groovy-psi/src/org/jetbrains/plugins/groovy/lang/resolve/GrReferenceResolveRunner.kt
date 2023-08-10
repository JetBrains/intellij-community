// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isThisExpression
import org.jetbrains.plugins.groovy.lang.psi.util.treeWalkUpAndGet
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT
import org.jetbrains.plugins.groovy.lang.resolve.processors.CodeFieldProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.LocalVariableProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.ReferenceExpressionClassProcessor
import org.jetbrains.plugins.groovy.transformations.inline.getHierarchicalInlineTransformationPerformer

class GrReferenceResolveRunner(val place: GrReferenceExpression, val processor: PsiScopeProcessor) {

  fun resolveReferenceExpression(): Boolean {
    val processNonCode = PsiTreeUtil.skipParentsOfType(
      place, GrReferenceExpression::class.java, GrAnnotationArrayInitializer::class.java
    ) !is GrAnnotationNameValuePair
    val initialState = initialState(processNonCode)
    val qualifier = place.qualifier
    if (qualifier == null) {
      if (!treeWalkUp(place, processor, initialState)) return false
      if (!processNonCode) return true
      if (place.context is GrMethodCall && !ClosureMissingMethodContributor.processMethodsFromClosures(place, processor)) return false
    }
    else {
      val state = initialState.put(RESOLVE_CONTEXT, qualifier)
      if (place.dotTokenType === GroovyTokenTypes.mSPREAD_DOT) {
        return qualifier.type.processSpread(processor, state, place, place.parent !is GrMethodCall)
      }
      else {
        if (ResolveUtil.isClassReference(place)) return false
        if (!processQualifier(qualifier, initialState)) return false
      }
    }
    if (processNonCode) {
      if (!ResolveUtil.processCategoryMembers(place, processor, initialState)) return false
      val macroPerformer = getHierarchicalInlineTransformationPerformer(place)
      if (macroPerformer != null && !macroPerformer.processResolve(processor, initialState, place)) return false
    }
    return true
  }

  private fun processQualifier(qualifier: GrExpression, state: ResolveState): Boolean {
    val qualifierType = qualifier.type
    if (qualifierType == null || PsiTypes.voidType() == qualifierType) {
      if (qualifier is GrReferenceExpression) {
        val resolved = qualifier.resolve()
        if (resolved is PsiClass) {
          if (!ResolveUtil.processClassDeclarations((resolved as PsiClass?)!!, processor, state, null, place)) return false
        }
        else if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) return false
        if (resolved !is PsiPackage) {
          val objectQualifier = TypesUtil.getJavaLangObject(place)
          if (!objectQualifier.processReceiverType(processor, state, place)) return false
        }
      }
    }
    else {
      if (!qualifierType.processReceiverType(processor, state, place)) return false
      if (place.parent !is GrMethodCall && !processImplicitSpread(qualifierType, processor, state, place)) {
        return false
      }
    }
    return true
  }
}

private fun processImplicitSpread(type: PsiType, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
      (type is PsiArrayType && !processor.checkName("length", state))) {
    return type.processSpread(processor, state, place, true)
  }
  else return true
}


private fun GrReferenceExpression.resolvePackageOrClass() = doResolvePackageOrClass()?.let(::ElementResolveResult)

private fun GrReferenceExpression.doResolvePackageOrClass(): PsiElement? {
  val qname = qualifiedReferenceName ?: return null

  val facade = JavaPsiFacade.getInstance(project)
  val scope = resolveScope

  if (isQualified) {
    val clazz = resolveClassFqn(facade, scope)
    clazz?.let { return it }
  }

  // We are in `com.foo` from `com.foo.bar.Baz`.
  // Go up and find if any parent resolves to a class => this expression is a package reference.
  // This expression may also be a class reference, and this is handled in [resolveUnqualifiedType].
  for (parent in this.parents(false)) {
    if (parent !is GrReferenceExpression) {
      // next parent is not a reference expression
      // => next parent is not a class fully qualified name
      // => this expression is not a package reference
      return null
    }
    val clazz = parent.resolveClassFqn(facade, scope)
    if (clazz != null) {
      return facade.findPackage(qname) ?: object : PsiPackageImpl(manager, qname) {
        override fun isValid(): Boolean = !manager.isDisposed
      }
    }
  }

  return null
}

private fun GrReferenceExpression.resolveClassFqn(facade: JavaPsiFacade, scope: GlobalSearchScope): PsiClass? {
  if (parent is GrMethodCall) return null
  val name = referenceName ?: return null
  if (name.isEmpty() || !name.first().isUpperCase()) return null
  val qname = qualifiedReferenceName ?: return null
  return facade.findClass(qname, scope)
}

/**
 * Resolves elements that exist before transformations are run.
 *
 * @see org.codehaus.groovy.control.ResolveVisitor
 */
internal fun GrReferenceExpression.doResolveStatic(): GroovyResolveResult? {
  val name = referenceName ?: return null

  val fqnResult = resolvePackageOrClass()
  if (fqnResult != null) {
    return fqnResult
  }

  val qualifier = qualifier

  if (qualifier == null) {
    val localVariable = resolveToLocalVariable(name)
    if (localVariable != null) {
      return localVariable
    }
    val macroResult = resolveInInlineTransformation(this)
    if (macroResult != null) {
      return macroResult
    }
  }

  if (parent !is GrMethodCall) {
    if (qualifier == null || qualifier.isThisExpression()) {
      val field = resolveToField(name)
      if (field != null && checkCurrentClass(field.element, this)) {
        return field
      }
    }
    if (qualifier == null) {
      // at this point:
      // - the reference is org.codehaus.groovy.ast.expr.VariableExpression
      // - the reference doesn't resolve to a variable, meaning it accesses org.codehaus.groovy.ast.DynamicVariable
      return resolveUnqualifiedType(name)
    }
    if (qualifier is GrReferenceExpression) {
      return resolveQualifiedType(name, qualifier)
    }
  }

  return null
}

/**
 * Walks up the tree and returns when the first [local variable][GrVariable] is found.
 *
 * @name local variable name
 * @receiver call site
 * @return empty collection or a collection with 1 local variable result
 */
fun PsiElement.resolveToLocalVariable(name: String): ElementResolveResult<GrVariable>? {
  return treeWalkUpAndGet(LocalVariableProcessor(name))
}

/**
 * Walks up the tree and returns when the first code [field][GrField] is found.
 *
 * @name field name
 * @receiver call site
 * @return empty collection or a collection with 1 code field result
 */
private fun PsiElement.resolveToField(name: String): ElementResolveResult<GrField>? {
  return treeWalkUpAndGet(CodeFieldProcessor(name, this))
}

/**
 * Checks if resolved [field] is a field of current class owner.
 *
 * @see org.codehaus.groovy.control.ResolveVisitor.currentClass
 */
private fun checkCurrentClass(field: GrField, place: PsiElement): Boolean {
  val containingClass = field.containingClass ?: return false
  return containingClass == place.getOwner()
}

/**
 * @see org.codehaus.groovy.control.ResolveVisitor.transformVariableExpression
 */
private fun PsiElement.resolveUnqualifiedType(name: String): GroovyResolveResult? {
  val processor = ReferenceExpressionClassProcessor(name, this)
  processUnqualified(processor, ResolveState.initial())
  return processor.result
}

private fun PsiElement.resolveQualifiedType(name: String, qualifier: GrReferenceExpression): GroovyResolveResult? {
  val classQualifier = qualifier.staticReference.resolve() as? PsiClass ?: return null
  val processor = ReferenceExpressionClassProcessor(name, this)
  classQualifier.processDeclarations(processor, ResolveState.initial(), null, this)
  return processor.result
}

private fun resolveInInlineTransformation(psiElement: PsiElement) : ElementResolveResult<PsiElement>? {
  val handler = getHierarchicalInlineTransformationPerformer(psiElement) ?: return null
  return handler.computeStaticReference(psiElement)
}