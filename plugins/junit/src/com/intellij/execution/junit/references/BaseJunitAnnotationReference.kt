// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.reference.PsiMemberReference
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.ClassUtil
import org.jetbrains.uast.*

/**
 * A base reference implementation for resolving and providing code completion
 * for string values in JUnit annotations such as `@MethodSource` or `@FieldSource`.
 *
 * This class supports references to members in the same class or in other classes,
 * including the use of fully qualified names like `"com.example.MyClass#methodName"`.
 *
 * Subclasses must provide logic for resolving specific member types
 * (e.g., methods for `@MethodSource`, fields for `@FieldSource`) by implementing the abstract methods.
 *
 * Responsibilities include:
 * - Resolving string references in JUnit annotations to actual `PsiMethod` or `PsiField` elements.
 * - Providing completion variants within the appropriate scope, avoiding duplicates or irrelevant symbols.
 * - Handling renames and refactorings of the referenced member.
 *
 * @param Psi the PSI type being resolved (e.g., `PsiMethod` or `PsiField`)
 * @param U the corresponding UAST declaration type (e.g., `UMethod` or `UField`)
 * @property element the string literal in a JUnit annotation that references a member
 */
abstract class BaseJunitAnnotationReference<Psi : PsiMember, U : UDeclaration>(
  element: PsiLanguageInjectionHost,
) : PsiReferenceBase<PsiLanguageInjectionHost>(element, false), PsiMemberReference, PsiPolyVariantReference {
  override fun bindToElement(element: PsiElement): PsiElement {
    if (element is PsiMember) {
      return handleElementRename(element.name ?: return super.bindToElement(element))
    }
    return super.bindToElement(element)
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    val className = StringUtil.getPackageName(value, '#')
    val selfClassReference = className.isEmpty() || ClassUtil.findPsiClass(element.manager, className, null, false, element.resolveScope) == null
    return super.handleElementRename(if (selfClassReference) newElementName else "$className#$newElementName")
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    val results: Array<ResolveResult> = multiResolve(false)
    for (result in results) {
      if (element == result.getElement()) {
        return true
      }
    }
    return false
  }

  override fun resolve(): PsiElement? {
    val results: Array<ResolveResult> = multiResolve(false)
    return if (results.size == 1) results[0].element else null
  }

  private fun filteredElements(elements: Array<Psi>, uClass: UClass, testMethod: UMethod?): List<Psi> {
    val noStaticProblem = elements.filter { hasNoStaticProblem(it, uClass, testMethod) }
    if (noStaticProblem.isNotEmpty()) return noStaticProblem
    return elements.toList()
  }

  override fun getVariants(): Array<Any> {
    val literal = element.toUElement(UExpression::class.java) ?: return emptyArray()
    val containingClass = literal.getParentOfType(UClass::class.java) ?: return emptyArray()
    val current = literal.getParentOfType(UMethod::class.java)
    val allEls = getAll(containingClass.javaPsi)
    return allEls.filter { element ->
      val aClass = element.containingClass ?: return@filter false
      CommonClassNames.JAVA_LANG_OBJECT != aClass.qualifiedName
      && element.name != current?.name
      && element.name != "Companion"
      && hasNoStaticProblem(element, containingClass, current)
    }.map { element ->
      LookupElementBuilder.create(element.name!!).withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT)
    }.toTypedArray()
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val file = element.containingFile ?: return ResolveResult.EMPTY_ARRAY
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, OurGenericsResolver, false, incompleteCode, file)
  }

  /**
   * Finds a method reference from a direct link in the format `com.example.MyClass$InnerClass#testMethod`.
   *
   * @param literal The expression from an annotation containing the method reference as a string.
   * @param scope The class scope used to resolve the method reference.
   * @return The resolved `PsiMethod` if found, or `null` if the method cannot be resolved or the link is not direct.
   */
  private fun directLink(literal: UExpression, scope: UClass, testMethod: UMethod?): Psi? {
    val string = literal.evaluate() as String? ?: return null
    val factoryClassName = StringUtil.getPackageName(string, '#')
    if (factoryClassName.isEmpty()) return null
    val factoryMethodName = StringUtil.getShortName(string, '#')
    if (factoryMethodName.isEmpty()) return null
    val factoryClass = ClassUtil.findPsiClass(scope.javaPsi.manager, factoryClassName, null, false, scope.javaPsi.resolveScope)
                       ?: return null
    val factoryMethods = getPsiElementsByName(factoryClass, factoryMethodName, false)
    return filteredElements(factoryMethods, scope, testMethod).firstOrNull()
  }

  private fun fastResolveFor(literal: UExpression, scope: UClass, testMethod: UMethod?): Set<PsiElement> {
    val name = literal.evaluate() as String? ?: return setOf()
    val psiClazz = scope.javaPsi
    val clazzElements = filteredElements(getPsiElementsByName(psiClazz, name, true), scope, testMethod)

    val elements = ClassInheritorsSearch.search(psiClazz, psiClazz.resolveScope, true)
      .mapNotNull { aClazz -> aClazz.toUElement(UClass::class.java) }
      .flatMap { uClazz -> filteredElements(getPsiElementsByName(uClazz.javaPsi, name, true), uClazz, testMethod) }
      .toMutableSet()
    elements.addAll(clazzElements)
    return elements
  }

  /**
   * @param testMethod test method marked with JUnit annotation
   * @return the method referenced from the annotation
   */
  private fun fastResolveFor(testMethod: UMethod): Set<PsiElement> {
    val literal = element.toUElement(UExpression::class.java) ?: return setOf()
    val scope = literal.getParentOfType(UClass::class.java) ?: return setOf()
    val directLink = directLink(literal, scope, testMethod)
    if (directLink != null) return setOf(directLink)
    val currentClass = testMethod.getParentOfType(UClass::class.java) ?: return setOf()
    return fastResolveFor(literal, currentClass, testMethod)
  }

  /**
   * @param element method/field referenced from within JUnit annotation
   * @param literalClazz the class where the annotation is located
   * @param literalMethod the JUnit annotated method is null in case the annotation is class-level
   * @return true in case a static check failed
   */
  protected abstract fun hasNoStaticProblem(element: Psi, literalClazz: UClass, literalMethod: UMethod?): Boolean
  protected abstract fun getPsiElementsByName(directClass: PsiClass, name: String, checkBases: Boolean): Array<Psi>
  protected abstract fun uType(): Class<U>
  protected abstract fun toTypedPsiArray(collection: Collection<Psi>): Array<Psi>
  protected abstract fun isPsiType(element: PsiElement): Boolean
  protected abstract fun getAll(directClass: PsiClass): Array<Psi>

  private object OurGenericsResolver : ResolveCache.PolyVariantResolver<BaseJunitAnnotationReference<out PsiMember, out UDeclaration>> {
    override fun resolve(ref: BaseJunitAnnotationReference<out PsiMember, out UDeclaration>, incompleteCode: Boolean): Array<ResolveResult> {
      val literal = ref.element.toUElement(UExpression::class.java) ?: return ResolveResult.EMPTY_ARRAY
      val uClass = literal.getParentOfType(UClass::class.java) ?: return ResolveResult.EMPTY_ARRAY
      val testMethod = literal.getParentOfType(UMethod::class.java)

      val directLink = ref.directLink(literal, uClass, testMethod)
      if (directLink != null) return arrayOf(PsiSourceResolveResult(directLink, listOf()))

      if (testMethod != null) { // direct annotation
        val owners = testMethod.javaPsi.containingClass?.let { listOf(it) } ?: emptyList()
        return ref.fastResolveFor(testMethod).filter { ref.isPsiType(it) }
          .map { PsiSourceResolveResult(it, owners) }.toTypedArray()
      }
      else if (uClass.isAnnotationType) { // inherited annotation from another annotation
        val scope = uClass.sourcePsi?.resolveScope ?: ref.element.resolveScope
        val process = ArrayDeque<PsiClass>()
        val processed = mutableSetOf<PsiClass>()
        val result = mutableSetOf<PsiMethod>()

        process.add(uClass)
        while (process.isNotEmpty()) {
          val current = process.removeFirst()
          if (!processed.add(current)) continue

          // find all the methods annotated with this annotation
          result.addAll(AnnotatedElementsSearch.searchPsiMethods(current, scope).findAll())

          // find all the classes and annotations annotated with this annotation in depth
          process.addAll(AnnotatedElementsSearch.searchPsiClasses(current, scope).findAll())
        }
        return result
          .mapNotNull { testMethod -> testMethod.toUElement(UMethod::class.java) }
          .mapNotNull { testMethod -> testMethod.getParentOfType(UClass::class.java) }
          .distinct() // process only classes
          .map { clazz -> clazz to ref.fastResolveFor(literal, clazz, testMethod) }
          .flatMap { (clazz, elements) -> elements.map { element -> element to clazz } }
          .groupBy({ it.first }, { it.second })
          .map { (elements, classes) -> PsiSourceResolveResult(elements, classes) }.toTypedArray()
      }
      else {
        val clazz = literal.getParentOfType(UClass::class.java)
        if (clazz != null) {
          val owners = clazz.javaPsi.containingClass?.let { listOf(it) } ?: emptyList()
          return ref.fastResolveFor(literal, clazz, testMethod).map { PsiSourceResolveResult(it, owners) }.toTypedArray()
        }
        else {
          return ResolveResult.EMPTY_ARRAY
        }
      }
    }
  }
}
