// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind.*
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.util.skipSameTypeParents
import org.jetbrains.plugins.groovy.lang.psi.util.treeWalkUp
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.StaticImport
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.CollectElementsProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.TypeParameterProcessor

// https://issues.apache.org/jira/browse/GROOVY-8358
// https://issues.apache.org/jira/browse/GROOVY-8359
// https://issues.apache.org/jira/browse/GROOVY-8361
// https://issues.apache.org/jira/browse/GROOVY-8362
// https://issues.apache.org/jira/browse/GROOVY-8364
// https://issues.apache.org/jira/browse/GROOVY-8365

internal object GrCodeReferenceResolver : GroovyResolver<GrCodeReferenceElement> {

  override fun resolve(ref: GrCodeReferenceElement, incomplete: Boolean): Collection<GroovyResolveResult> {
    return when (ref.kind) {
      PACKAGE_REFERENCE -> ref.resolveAsPackageReference()
      IMPORT_REFERENCE -> ref.resolveAsImportReference()
      REFERENCE -> ref.resolveReference()
    }
  }
}

private fun GrCodeReferenceElement.resolveAsPackageReference(): Collection<GroovyResolveResult> {
  val fqn = qualifiedReferenceName ?: return emptyList()
  val pckg = JavaPsiFacade.getInstance(project).findPackage(fqn) ?: return emptyList()
  return listOf(ElementGroovyResult(pckg))
}

private fun GrCodeReferenceElement.resolveAsImportReference(): Collection<GroovyResolveResult> {
  val file = containingFile as? GroovyFile ?: return emptyList()

  val statement = parentOfType<GrImportStatement>() ?: return emptyList()
  val topLevelReference = statement.importReference ?: return emptyList()
  val import = statement.import ?: return emptyList()

  if (this === topLevelReference) {
    return if (import is StaticImport) {
      resolveStaticImportReference(file, import)
    }
    else {
      resolveImportReference(file, import)
    }
  }
  if (parent === topLevelReference && import is StaticImport) {
    return resolveImportReference(file, import)
  }
  if (import is StarImport) {
    // reference inside star import
    return resolveAsPackageReference()
  }

  val clazz = import.resolveImport(file) as? PsiClass
  val classReference = if (import is StaticImport) topLevelReference.qualifier else topLevelReference
  if (clazz == null || classReference == null) return resolveAsPackageReference()
  return resolveAsPartOfFqn(classReference, clazz)
}

private fun resolveStaticImportReference(file: GroovyFile, import: StaticImport): Collection<GroovyResolveResult> {
  val processor = CollectElementsProcessor()
  import.processDeclarations(processor, ResolveState.initial(), file, file)
  return processor.results.collapseReflectedMethods().collapseAccessors().map(::ElementGroovyResult)
}

private fun resolveImportReference(file: GroovyFile, import: GroovyImport): Collection<GroovyResolveResult> {
  val resolved = import.resolveImport(file) ?: return emptyList()
  return listOf(ElementGroovyResult(resolved))
}

private fun GrCodeReferenceElement.resolveReference(): Collection<GroovyResolveResult> {
  val name = referenceName ?: return emptyList()

  if (canResolveToTypeParameter()) {
    val typeParameters = resolveToTypeParameter(this, name)
    if (typeParameters.isNotEmpty()) return typeParameters
  }

  val (_, outerMostReference) = skipSameTypeParents()
  if (outerMostReference !== this) {
    val fqnReferencedClass = outerMostReference.resolveClassFqn()
    if (fqnReferencedClass != null) {
      return resolveAsPartOfFqn(outerMostReference, fqnReferencedClass)
    }
  }
  else if (isQualified) {
    val clazz = resolveClassFqn()
    if (clazz != null) {
      val substitutor = PsiSubstitutor.EMPTY.putAll(clazz, typeArguments)
      return listOf(ClassResolveResult(clazz, this, null, substitutor))
    }
  }

  val processor = ClassProcessor(name, this, typeArguments, isAnnotationReference())
  val state = ResolveState.initial()
  processClasses(processor, state)
  val classes = processor.results
  if (classes.isNotEmpty()) return classes

  if (canResolveToPackage()) {
    val packages = resolveAsPackageReference()
    if (packages.isNotEmpty()) return packages
  }

  return emptyList()
}

private fun GrReferenceElement<*>.canResolveToTypeParameter(): Boolean {
  if (isQualified) return false
  val parent = parent
  return parent !is GrReferenceElement<*> &&
         parent !is GrExtendsClause &&
         parent !is GrImplementsClause &&
         parent !is GrAnnotation &&
         parent !is GrImportStatement &&
         parent !is GrNewExpression &&
         parent !is GrAnonymousClassDefinition &&
         parent !is GrCodeReferenceElement
}

private fun resolveToTypeParameter(place: PsiElement, name: String): Collection<GroovyResolveResult> {
  val processor = TypeParameterProcessor(name)
  place.treeWalkUp(processor)
  return processor.results
}

private fun GrReferenceElement<*>.canResolveToPackage(): Boolean = parent is GrReferenceElement<*>

private fun GrCodeReferenceElement.resolveAsPartOfFqn(reference: GrCodeReferenceElement, clazz: PsiClass): Collection<GroovyResolveResult> {
  var currentReference = reference
  var currentElement: PsiNamedElement = clazz
  while (currentReference !== this) {
    currentReference = currentReference.qualifier ?: return emptyList()
    val e: PsiNamedElement? = when (currentElement) {
      is PsiClass -> currentElement.containingClass ?: currentElement.getPackage()
      is PsiPackage -> currentElement.parentPackage
      else -> null
    }
    currentElement = e ?: return emptyList()
  }
  return listOf(BaseGroovyResolveResult(currentElement, this, null))
}

private fun PsiClass.getPackage(): PsiPackage? {
  val file = containingFile
  val name = (file as? PsiClassOwner)?.packageName ?: return null
  return JavaPsiFacade.getInstance(file.project).findPackage(name)
}

fun GrCodeReferenceElement.processClasses(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val qualifier = qualifier
  if (qualifier == null) {
    return processUnqualified(processor, state)
  }
  else {
    return processQualifier(qualifier, processor, state)
  }
}

private fun GrCodeReferenceElement.processUnqualified(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  return processInnerClasses(processor, state) &&
         processFileLevelDeclarations(processor, state)
}

/**
 * @see org.codehaus.groovy.control.ResolveVisitor.resolveNestedClass
 */
private fun GrCodeReferenceElement.processInnerClasses(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val currentClass = contextOfType<GrTypeDefinition>() ?: return true

  if (canResolveToInnerClassOfCurrentClass()) {
    if (!currentClass.processInnerInHierarchy(processor, state, this)) return false
  }

  return currentClass.processInnersInOuters(processor, state, this)
}

/**
 * @see org.codehaus.groovy.control.ResolveVisitor.resolveFromModule
 * @see org.codehaus.groovy.control.ResolveVisitor.resolveFromDefaultImports
 */
private fun GrCodeReferenceElement.processFileLevelDeclarations(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  // There is no point in processing imports in dummy files.
  val file = containingFile.skipDummies() ?: return true
  return file.treeWalkUp(processor, state, this)
}

private fun GrCodeReferenceElement.processQualifier(qualifier: GrCodeReferenceElement,
                                                    processor: PsiScopeProcessor,
                                                    state: ResolveState): Boolean {
  for (result in qualifier.multiResolve(false)) {
    val clazz = result.element as? PsiClass ?: continue
    if (!clazz.processDeclarations(processor, state.put(PsiSubstitutor.KEY, result.substitutor), null, this)) return false
  }
  return true
}

private fun GrCodeReferenceElement.canResolveToInnerClassOfCurrentClass(): Boolean {
  val parent = getActualParent()
  return parent !is GrExtendsClause &&
         parent !is GrImplementsClause &&
         (parent !is GrAnnotation || parent.classReference != this) && // annotation's can't be inner classes of current class
         (parent !is GrAnonymousClassDefinition || parent.baseClassReferenceGroovy != this)
}

/**
 * Reference element may be created from stub. In this case containing file will be dummy, and its context will be reference parent
 */
private fun GrCodeReferenceElement.getActualParent(): PsiElement? = containingFile.context ?: parent

private fun PsiFile?.skipDummies(): PsiFile? {
  var file: PsiFile? = this
  while (file != null && !file.isPhysical) {
    val context = file.context
    if (context == null) return file
    file = context.containingFile
  }
  return file
}
