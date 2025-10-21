// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LoopToCallChain")
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.psi.util.skipSameTypeParents
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty
import org.jetbrains.plugins.groovy.lang.resolve.imports.importedNameKey
import org.jetbrains.plugins.groovy.lang.resolve.processors.DynamicMembersHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind

val log: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.resolve")

@JvmField
val NON_CODE: Key<Boolean?> = Key.create("groovy.process.non.code.members")

@JvmField
val PATTERN_VARIABLE: Key<Boolean?> = Key.create("groovy.process.pattern.variables")

@JvmField
val sorryCannotKnowElementKind: Key<Boolean> = Key.create("groovy.skip.kind.check.please")

private val IGNORE_IMPORTS : Key<Unit> = Key.create("groovy.defer.imports")

fun initialState(processNonCodeMembers: Boolean): ResolveState = ResolveState.initial().put(NON_CODE, processNonCodeMembers)

fun ResolveState.processNonCodeMembers(): Boolean = get(NON_CODE).let { it == null || it }

fun ResolveState.shouldProcessPatternVariables(): Boolean = get(PATTERN_VARIABLE).let { it == null || it }

fun ResolveState.ignoreImports() : ResolveState = put(IGNORE_IMPORTS, Unit)

fun ResolveState.areImportsIgnored(): Boolean = get(IGNORE_IMPORTS) != null

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
  if (!processor.shouldProcessLocals()) return true
  val newState = state.put(PATTERN_VARIABLE, false)
  val result = processStatements(lastParent) {
    it.processDeclarations(processor, newState, null, place)
  }
  return result
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

fun PsiScopeProcessor.shouldProcessFields(): Boolean = shouldProcess(GroovyResolveKind.FIELD)

fun PsiScopeProcessor.shouldProcessMethods(): Boolean = shouldProcess(GroovyResolveKind.METHOD)

fun PsiScopeProcessor.shouldProcessProperties(): Boolean = shouldProcess(GroovyResolveKind.PROPERTY)

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

private fun PsiScopeProcessor.shouldProcess(kind: GroovyResolveKind): Boolean {
  val resolveKindHint = getHint(GroovyResolveKind.HINT_KEY)
  if (resolveKindHint != null) return resolveKindHint.shouldProcess(kind)

  val elementClassHint = getHint(ElementClassHint.KEY) ?: return true
  return kind.declarationKinds.any(elementClassHint::shouldProcess)
}

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

fun getName(state: ResolveState, element: PsiElement): String? {
  return state[importedNameKey] ?: element.asSafely<PsiNamedElement>()?.name ?: element.asSafely<GrReferenceElement<*>>()?.referenceName
}

fun <T : GroovyResolveResult> valid(allCandidates: Collection<T>): List<T> = allCandidates.filter {
  it.isValidResult
}

fun singleOrValid(allCandidates: List<GroovyResolveResult>): List<GroovyResolveResult> {
  return if (allCandidates.size <= 1) allCandidates else valid(allCandidates)
}

fun getResolveKind(element: PsiElement): GroovyResolveKind? {
  return when (element) {
    is PsiClass -> GroovyResolveKind.CLASS
    is PsiPackage -> GroovyResolveKind.PACKAGE
    is PsiMethod -> GroovyResolveKind.METHOD
    is PsiField -> GroovyResolveKind.FIELD
    is GrBindingVariable -> GroovyResolveKind.BINDING
    is PsiVariable -> GroovyResolveKind.VARIABLE
    is GroovyProperty -> GroovyResolveKind.PROPERTY
    is GrReferenceElement<*> -> GroovyResolveKind.PROPERTY
    else -> null
  }
}

fun GroovyResolveResult?.asJavaClassResult(): PsiClassType.ClassResolveResult {
  if (this == null) return PsiClassType.ClassResolveResult.EMPTY
  val clazz = element as? PsiClass ?: return PsiClassType.ClassResolveResult.EMPTY
  return object : PsiClassType.ClassResolveResult {
    override fun getElement(): PsiClass = clazz
    override fun getSubstitutor(): PsiSubstitutor = this@asJavaClassResult.substitutor
    override fun isPackagePrefixPackageReference(): Boolean = false
    override fun isAccessible(): Boolean = true
    override fun isStaticsScopeCorrect(): Boolean = true
    override fun getCurrentFileResolveScope(): PsiElement? = null
    override fun isValidResult(): Boolean = true
  }
}

fun markAsReferenceResolveTarget(refExpr: GrReferenceElement<*>) {
  refExpr.putUserData(REFERENCE_RESOLVE_TARGET, Unit)
}

internal fun isReferenceResolveTarget(refExpr: GrReferenceElement<*>) : Boolean {
  return refExpr.getUserData(REFERENCE_RESOLVE_TARGET) != null
}

private val REFERENCE_RESOLVE_TARGET : Key<Unit> = Key.create("Reference resolve target")
