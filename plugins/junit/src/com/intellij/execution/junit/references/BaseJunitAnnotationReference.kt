// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.reference.PsiMemberReference
import com.intellij.lang.jvm.JvmMethod
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList
import org.jetbrains.uast.*

abstract class BaseJunitAnnotationReference(
  element: PsiLanguageInjectionHost
) : PsiReferenceBase<PsiLanguageInjectionHost>(element, false), PsiMemberReference {
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
    val myLiteral = getElement().toUElement(UExpression::class.java) ?: return false
    val uMethod = element.toUElement(UMethod::class.java) ?: return false
    val method = uMethod.javaPsi
    val methodName = myLiteral.evaluate() as String? ?: return false
    val shortName = StringUtil.getShortName(methodName, '#')
    if (shortName != method.name) return false
    val methodClass = method.containingClass ?: return false
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      val className = StringUtil.getPackageName(methodName, '#')
      if (!className.isEmpty()) {
        return className == ClassUtil.getJVMClassName(methodClass)
      }
    }
    val literalClazz = myLiteral.getParentOfType(UClass::class.java) ?: return false
    val psiClazz = literalClazz.javaPsi
    return InheritanceUtil.isInheritorOrSelf(psiClazz, methodClass, true) ||
           InheritanceUtil.isInheritorOrSelf(methodClass, psiClazz, true)
  }

  override fun resolve(): PsiElement? {
    val myLiteral = element.toUElement(UExpression::class.java) ?: return null
    val literalClazz = myLiteral.getParentOfType(UClass::class.java) ?: return null
    val literalMethod = myLiteral.getParentOfType(UMethod::class.java)
    var methodName = myLiteral.evaluate() as String? ?: return null
    val className = StringUtil.getPackageName(methodName, '#')
    var psiClazz = literalClazz.javaPsi
    if (!className.isEmpty()) {
      val aClass = ClassUtil.findPsiClass(psiClazz.manager, className, null, false, psiClazz.resolveScope)
      if (aClass != null) {
        psiClazz = aClass
        methodName = StringUtil.getShortName(methodName, '#')
      }
    }
    val finalMethodName = methodName
    var clazzMethods = psiClazz.findMethodsByName(methodName, true)
    if (clazzMethods.isEmpty()) {
      val classes = psiClazz.innerClasses
      val methodsInner = SmartList<JvmMethod>()
      for (cl in classes) {
        val name = cl.findMethodsByName(finalMethodName)
        if (name.isNotEmpty()) {
          methodsInner.addAll(listOf(*name))
        }
      }
      if (methodsInner.size > 0) {
        clazzMethods = methodsInner.mapNotNull { method -> method as? PsiMethod? }.toTypedArray()
      }
    }
    if (clazzMethods.isEmpty() && (psiClazz.isInterface || PsiUtil.isAbstractClass(psiClazz))) {
      return ClassInheritorsSearch.search(psiClazz, psiClazz.resolveScope, false)
               .mapNotNull { aClazz ->
                 val methods = aClazz.findMethodsByName(finalMethodName, false)
                 filteredMethod(methods, literalClazz, literalMethod)
               }
               .map { method -> PsiElementResolveResult(method) }
               .firstOrNull()?.element ?: return null
    }
    return filteredMethod(clazzMethods, literalClazz, literalMethod)
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

  /**
   * @param method method referenced from within JUnit annotation
   * @param literalClazz the class where the annotation is located
   * @param literalMethod the JUnit annotated method, is null in case the annotation is class-level
   * @return true in case static check is successful
   */
  protected abstract fun hasNoStaticProblem(method: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean
}
