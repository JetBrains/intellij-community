// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.*
import com.intellij.psi.util.InheritanceUtil.isInheritor
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
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isThisExpression
import org.jetbrains.plugins.groovy.lang.psi.util.treeWalkUpAndGet
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.canResolveToMethod
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isDefinitelyKeyOfMap
import org.jetbrains.plugins.groovy.lang.resolve.processors.*

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
      val state = initialState.put(ClassHint.RESOLVE_CONTEXT, qualifier)
      if (place.dotTokenType === GroovyTokenTypes.mSPREAD_DOT) {
        return qualifier.type.processSpread(processor, state, place, place.parent !is GrMethodCall)
      }
      else {
        if (ResolveUtil.isClassReference(place)) return false
        if (!processJavaLangClass(qualifier, initialState)) return false
        if (!processQualifier(qualifier, initialState)) return false
      }
    }
    if (processNonCode) {
      if (!ResolveUtil.processCategoryMembers(place, processor, initialState)) return false
    }
    return true
  }

  private fun processJavaLangClass(qualifier: GrExpression, initialState: ResolveState): Boolean {
    if (qualifier !is GrReferenceExpression) return true

    //optimization: only 'class' or 'this' in static context can be an alias of java.lang.Class
    if ("class" != qualifier.referenceName && !PsiUtil.isThisReference(qualifier) && qualifier.resolve() !is PsiClass) return true

    val classType = ResolveUtil.unwrapClassType(qualifier.type)
    return classType.processReceiverType(processor, initialState, place)
  }

  private fun processQualifier(qualifier: GrExpression, state: ResolveState): Boolean {
    val qualifierType = qualifier.type
    if (qualifierType == null || PsiType.VOID == qualifierType) {
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
      if (place.parent !is GrMethodCall && isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        return qualifierType.processSpread(processor, state, place, true)
      }
    }
    return true
  }
}

fun GrReferenceExpression.getCallVariants(upToArgument: GrExpression?): Array<out GroovyResolveResult> {
  val processor = GroovyResolverProcessorBuilder.builder()
    .setAllVariants(true)
    .build(this)
  GrReferenceResolveRunner(this, processor).resolveReferenceExpression()
  return processor.candidatesArray
}

fun GrReferenceExpression.resolveReferenceExpression(forceRValue: Boolean, incomplete: Boolean): Collection<GroovyResolveResult> {
  resolveStatic()?.let {
    return listOf(it)
  }

  if (!canResolveToMethod(this) && isDefinitelyKeyOfMap(this)) return emptyList()
  val processor = GroovyResolverProcessorBuilder.builder()
    .setForceRValue(forceRValue)
    .setIncomplete(incomplete)
    .build(this)
  GrReferenceResolveRunner(this, processor).resolveReferenceExpression()
  return processor.candidates
}

private fun GrReferenceExpression.resolvePackageOrClass() = doResolvePackageOrClass()?.let(::ElementResolveResult)

private fun GrReferenceExpression.doResolvePackageOrClass(): PsiElement? {
  val facade = JavaPsiFacade.getInstance(project)
  val scope = resolveScope

  fun GrReferenceExpression.resolveClass(): PsiClass? {
    if (parent is GrMethodCall) return null
    val name = referenceName ?: return null
    if (name.isEmpty() || !name.first().isUpperCase()) return null
    val qname = qualifiedReferenceName ?: return null
    return facade.findClass(qname, scope)
  }

  if (isQualified) {
    resolveClass()?.let { return it }
  }

  for (parent in strictParents()) {
    if (parent !is GrReferenceExpression) return null
    if (parent.resolveClass() == null) continue
    val qname = qualifiedReferenceName!!
    return facade.findPackage(qname)
  }

  return null
}

/**
 * Resolves elements that exist before transformations are run.
 *
 * @see org.codehaus.groovy.control.ResolveVisitor
 */
private fun GrReferenceExpression.resolveStatic(): GroovyResolveResult? {
  return CachedValuesManager.getCachedValue(this) {
    CachedValueProvider.Result.create(doResolveStatic(), this, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
  }
}

private fun GrReferenceExpression.doResolveStatic(): GroovyResolveResult? {
  val name = referenceName ?: return null

  val fqnResult = resolvePackageOrClass()
  if (fqnResult != null) {
    return fqnResult
  }

  val qualifier = qualifier

  if (qualifier == null) {
    val localVariable = resolveToLocalVariable(name).singleOrNull()
    if (localVariable != null) {
      return localVariable
    }
  }

  if (parent !is GrMethodCall) {
    if (qualifier == null || qualifier.isThisExpression()) {
      val field = resolveToField(name).singleOrNull()
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
private fun PsiElement.resolveToLocalVariable(name: String): Collection<ElementResolveResult<GrVariable>> {
  return treeWalkUpAndGet(LocalVariableProcessor(name))
}

/**
 * Walks up the tree and returns when the first code [field][GrField] is found.
 *
 * @name field name
 * @receiver call site
 * @return empty collection or a collection with 1 code field result
 */
private fun PsiElement.resolveToField(name: String): Collection<ElementResolveResult<GrField>> {
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
private fun PsiElement.resolveUnqualifiedType(name: String): ClassResolveResult? {
  val processor = ReferenceExpressionClassProcessor(name, this)
  processUnqualified(processor, ResolveState.initial())
  return processor.result
}

private fun PsiElement.resolveQualifiedType(name: String, qualifier: GrReferenceExpression): ClassResolveResult? {
  val classQualifier = qualifier.resolveStatic()?.element as? PsiClass ?: return null
  val processor = ReferenceExpressionClassProcessor(name, this)
  classQualifier.processDeclarations(processor, ResolveState.initial(), null, this)
  return processor.result
}
