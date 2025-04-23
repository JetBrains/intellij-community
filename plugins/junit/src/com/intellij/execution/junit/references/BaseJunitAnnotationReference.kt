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
import com.intellij.psi.util.PsiUtil
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
    val literal = getElement().toUElement(UExpression::class.java) ?: return false
    val scope = element.toUElement(UMethod::class.java)?.getParentOfType(UClass::class.java) ?: return false
    return directLink(literal, scope) == element ||
           fastResolveFor(literal, scope) == element
  }

  override fun resolve(): PsiElement? {
    val results: Array<ResolveResult> = multiResolve(false)
    return if (results.size == 1) results[0].element else null
  }

  private fun filteredMethod(clazzMethods: Array<PsiMethod>, uClass: UClass, uMethod: UMethod?): PsiMethod? {
    return clazzMethods.firstOrNull { method ->
      hasNoStaticProblem(method, uClass, uMethod)
    } ?: if (clazzMethods.isEmpty()) null else clazzMethods.first()
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
    val directUClass = directClass.toUElement(UClass::class.java) ?: return null
    return filteredMethod(directClass.findMethodsByName(methodName, false), directUClass, literal.getParentOfType(UMethod::class.java))
  }

  private fun fastResolveFor(literal: UExpression, scope: UClass): PsiElement? {
    val methodName = literal.evaluate() as String? ?: return null
    val psiClazz = scope.javaPsi
    val clazzMethods = psiClazz.findMethodsByName(methodName, true)
    if (clazzMethods.isEmpty() && (scope.isInterface || PsiUtil.isAbstractClass(psiClazz))) {
      val methods = ClassInheritorsSearch.search(psiClazz, psiClazz.resolveScope, false)
        .findAll()
        .flatMap { aClazz -> aClazz.findMethodsByName(methodName, false).toList() }
          return filteredMethod(methods.toTypedArray(), scope, literal.getParentOfType(UMethod::class.java))
    }
    return filteredMethod(clazzMethods, scope, literal.getParentOfType(UMethod::class.java))
  }

  /**
   * @param testMethod test method marked with JUnit annotation
   * @return the method referenced from the annotation
   */
  fun fastResolveFor(testMethod: UMethod): PsiElement? {
    val literal = element.toUElement(UExpression::class.java) ?: return null
    val scope = literal.getParentOfType(UClass::class.java) ?: return null
    val directLink = directLink(literal, scope)
    if (directLink != null) return directLink
    var currentClass = testMethod.getParentOfType(UClass::class.java) ?: return null
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
      if (directLink != null) return arrayOf(PsiElementResolveResult(directLink))

      val method = literal.getParentOfType(UMethod::class.java)
      if (method != null) { // direct annotation
        val resolved = ref.fastResolveFor(method)
        return if (resolved is PsiMethod)  arrayOf(PsiElementResolveResult(resolved)) else ResolveResult.EMPTY_ARRAY
      } else if (uClass.isAnnotationType) { // inherited annotation from another annotation
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
          .mapNotNull { clazz -> ref.fastResolveFor(literal, clazz) }
          .map { method -> PsiElementResolveResult(method) }.toTypedArray()
      } else {
        return ResolveResult.EMPTY_ARRAY
      }
    }
  }
}
