/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:Suppress("LoopToCallChain")

package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.psi.util.skipSameTypeParents
import org.jetbrains.plugins.groovy.lang.resolve.processors.DynamicMembersHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor

@JvmField
val NON_CODE = Key.create<Boolean?>("groovy.process.non.code.members")

fun initialState(processNonCodeMembers: Boolean) = ResolveState.initial().put(NON_CODE, processNonCodeMembers)

fun ResolveState.processNonCodeMembers(): Boolean = get(NON_CODE).let { it == null || it }

fun treeWalkUp(place: PsiElement, processor: PsiScopeProcessor, state: ResolveState): Boolean {
  return ResolveUtil.treeWalkUp(place, place, processor, state)
}

fun GrStatementOwner.processStatements(lastParent: PsiElement?, processor: (GrStatement) -> Boolean): Boolean {
  var run = if (lastParent == null) lastChild else lastParent.prevSibling
  while (run != null) {
    if (run is GrStatement && !processor(run)) return false
    run = run.prevSibling
  }
  return true
}

fun GrStatementOwner.processLocals(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
  return !processor.shouldProcessLocals() || processStatements(lastParent) {
    it.processDeclarations(processor, state, null, place)
  }
}

fun PsiScopeProcessor.checkName(name: String, state: ResolveState): Boolean {
  val expectedName = getName(state) ?: return true
  return expectedName == name
}

fun PsiScopeProcessor.getName(state: ResolveState): String? = getHint(NameHint.KEY)?.getName(state)

fun shouldProcessDynamicMethods(processor: PsiScopeProcessor): Boolean {
  return processor.getHint(DynamicMembersHint.KEY)?.shouldProcessMethods() ?: false
}

fun PsiScopeProcessor.shouldProcessDynamicProperties(): Boolean {
  return getHint(DynamicMembersHint.KEY)?.shouldProcessProperties() ?: false
}

fun PsiScopeProcessor.shouldProcessLocals(): Boolean = shouldProcess(GroovyResolveKind.VARIABLE)

fun PsiScopeProcessor.shouldProcessMethods(): Boolean {
  return ResolveUtil.shouldProcessMethods(getHint(ElementClassHint.KEY))
}

fun PsiScopeProcessor.shouldProcessClasses(): Boolean {
  return ResolveUtil.shouldProcessClasses(getHint(ElementClassHint.KEY))
}

fun PsiScopeProcessor.shouldProcessMembers(): Boolean {
  val hint = getHint(ElementClassHint.KEY) ?: return true
  return hint.shouldProcess(DeclarationKind.CLASS) ||
         hint.shouldProcess(DeclarationKind.FIELD) ||
         hint.shouldProcess(DeclarationKind.METHOD)
}

fun PsiScopeProcessor.shouldProcessTypeParameters(): Boolean {
  if (shouldProcessClasses()) return true
  val groovyKindHint = getHint(GroovyResolveKind.HINT_KEY) ?: return true
  return groovyKindHint.shouldProcess(GroovyResolveKind.TYPE_PARAMETER)
}

fun PsiScopeProcessor.shouldProcessProperties(): Boolean {
  return this is GroovyResolverProcessor && isPropertyResolve
}

fun PsiScopeProcessor.shouldProcessPackages(): Boolean = shouldProcess(GroovyResolveKind.PACKAGE)

private fun PsiScopeProcessor.shouldProcess(kind: GroovyResolveKind): Boolean {
  val resolveKindHint = getHint(GroovyResolveKind.HINT_KEY)
  if (resolveKindHint != null) return resolveKindHint.shouldProcess(kind)

  val elementClassHint = getHint(ElementClassHint.KEY) ?: return true
  return kind.declarationKinds.any(elementClassHint::shouldProcess)
}

fun wrapClassType(type: PsiType, context: PsiElement) = TypesUtil.createJavaLangClassType(type, context.project, context.resolveScope)

fun getDefaultConstructor(clazz: PsiClass): PsiMethod {
  return getCachedValue(clazz) {
    Result.create(DefaultConstructor(clazz), clazz)
  }
}

fun GroovyFileBase.processClassesInFile(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  if (!processor.shouldProcessClasses()) return true
  val scriptClass = scriptClass
  if (scriptClass != null && !ResolveUtil.processElement(processor, scriptClass, state)) return false
  for (definition in typeDefinitions) {
    if (!ResolveUtil.processElement(processor, definition, state)) return false
  }
  return true
}

fun GroovyFileBase.processClassesInPackage(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement = this): Boolean {
  if (!processor.shouldProcessClasses()) return true
  val aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName) ?: return true
  return aPackage.processDeclarations(PackageSkippingProcessor(processor), state, null, place)
}

val PsiScopeProcessor.annotationHint: AnnotationHint? get() = getHint(AnnotationHint.HINT_KEY)

fun PsiScopeProcessor.isAnnotationResolve(): Boolean {
  val hint = annotationHint ?: return false
  return hint.isAnnotationResolve
}

fun PsiScopeProcessor.isNonAnnotationResolve(): Boolean {
  val hint = annotationHint ?: return false
  return !hint.isAnnotationResolve
}

fun GrCodeReferenceElement.isAnnotationReference(): Boolean {
  val (possibleAnnotation, _) = skipSameTypeParents()
  return possibleAnnotation is GrAnnotation
}
