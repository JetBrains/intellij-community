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

abstract class BaseJunitAnnotationReference(
  element: PsiLanguageInjectionHost,
) : PsiReferenceBase<PsiLanguageInjectionHost>(element, false), PsiMemberReference, PsiPolyVariantReference {
  override fun bindToElement(element: PsiElement): PsiElement {
    if (element is PsiMethod) {
      return handleElementRename(element.name)
    }
    return super.bindToElement(element)
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    val methodName = value
    val className = StringUtil.getPackageName(methodName, '#')
    val selfClassReference = className.isEmpty() || ClassUtil.findPsiClass(
      element.manager, className, null, false, element.resolveScope
    ) == null
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

  override fun getVariants(): Array<Any> {
    val myLiteral = element.toUElement(UExpression::class.java) ?: return emptyArray()
    val topLevelClass = myLiteral.getParentOfType(UClass::class.java) ?: return emptyArray()
    val current = myLiteral.getParentOfType(UMethod::class.java)
    val psiTopLevelClass = topLevelClass.javaPsi
    val methods = psiTopLevelClass.allMethods
    val list = mutableListOf<Any>()
    for (method in methods) {
      val aClass = method.containingClass ?: continue
      if (CommonClassNames.JAVA_LANG_OBJECT == aClass.qualifiedName) continue
      if (current != null && method.name == current.name) continue
      if (current != null && !hasNoStaticProblem(method, topLevelClass, current)) continue
      val builder = LookupElementBuilder.create(method)
      list.add(builder.withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT))
    }
    return list.toTypedArray()
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val file: PsiFile = element.containingFile ?: return ResolveResult.EMPTY_ARRAY
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, OurGenericsResolver, false, incompleteCode, file)
  }

  /**
   * Finds a method reference from a direct link in the format `com.example.MyClass$InnerClass#testMethod`.
   *
   * @param literal The expression from an annotation containing the method reference as a string.
   * @param scope The class scope used to resolve the method reference.
   * @return The resolved `PsiMethod` if found, or `null` if the method cannot be resolved or the link is not direct.
   */
  private fun directLink(literal: UExpression, scope: UClass): PsiMethod? {
    val string = literal.evaluate() as String? ?: return null
    val className = StringUtil.getPackageName(string, '#')
    if (className.isEmpty()) return null
    val methodName = StringUtil.getShortName(string, '#')
    if (methodName.isEmpty()) return null
    val directClass = ClassUtil.findPsiClass(scope.javaPsi.manager, className, null, false, scope.javaPsi.resolveScope) ?: return null
    return directClass.findMethodsByName(methodName, false).firstOrNull()
  }

  private fun fastResolveFor(literal: UExpression, scope: UClass): Set<PsiMethod> {
    val methodName = literal.evaluate() as String? ?: return setOf()
    val psiClazz = scope.javaPsi
    val clazzMethods = psiClazz.findMethodsByName(methodName, true)

    val methods = ClassInheritorsSearch.search(psiClazz, psiClazz.resolveScope, true)
      .findAll()
      .flatMap { aClazz -> aClazz.findMethodsByName(methodName, true).toList() }
      .toMutableSet()
    methods.addAll(clazzMethods)
    return methods
  }

  /**
   * @param testMethod test method marked with JUnit annotation
   * @return the method referenced from the annotation
   */
  private fun fastResolveFor(testMethod: UMethod): Set<PsiMethod> {
    val literal = element.toUElement(UExpression::class.java) ?: return setOf()
    val scope = literal.getParentOfType(UClass::class.java) ?: return setOf()
    val directLink = directLink(literal, scope)
    if (directLink != null) return setOf(directLink)
    val currentClass = testMethod.getParentOfType(UClass::class.java) ?: return setOf()
    return fastResolveFor(literal, currentClass)
  }

  /**
   * @param method method referenced from within JUnit annotation
   * @param literalClazz the class where the annotation is located
   * @param literalMethod the JUnit annotated method is null in case the annotation is class-level
   * @return true in case a static check is successful
   */
  protected abstract fun hasNoStaticProblem(method: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean

  private object OurGenericsResolver: ResolveCache.PolyVariantResolver<BaseJunitAnnotationReference> {

    override fun resolve(ref: BaseJunitAnnotationReference, incompleteCode: Boolean): Array<ResolveResult> {
      val literal = ref.element.toUElement(UExpression::class.java) ?: return ResolveResult.EMPTY_ARRAY
      val uClass = literal.getParentOfType(UClass::class.java) ?: return ResolveResult.EMPTY_ARRAY
      val directLink = ref.directLink(literal, uClass)
      if (directLink != null) return arrayOf(PsiMethodSourceResolveResult(directLink, listOf()))

      val method = literal.getParentOfType(UMethod::class.java)
      if (method != null) { // direct annotation
        val owners = method.javaPsi.containingClass?.let { listOf(it) } ?: emptyList()
        return ref.fastResolveFor(method).map { PsiMethodSourceResolveResult(it, owners) }.toTypedArray()
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
          .mapNotNull { method -> method.toUElement(UMethod::class.java) }
          .mapNotNull { method -> method.getParentOfType(UClass::class.java) }
          .distinct() // process only classes
          .map{ clazz -> clazz to ref.fastResolveFor(literal, clazz) }
          .flatMap { (clazz, methods) -> methods.map { method -> method to clazz } }
          .groupBy({ it.first }, { it.second })
          .map { (method, classes) -> PsiMethodSourceResolveResult(method, classes) }.toTypedArray()
      }
      else {
        return ResolveResult.EMPTY_ARRAY
      }
    }
  }
}
