// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.light.LightPsiClassBuilder
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY
import com.intellij.util.containers.FactoryMap
import com.intellij.util.containers.toArray
import gnu.trove.THashSet
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getAnnotation
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.*
import org.jetbrains.plugins.groovy.transformations.dsl.MemberBuilder
import java.util.*

internal class TransformationContextImpl(private val myCodeClass: GrTypeDefinition) : TransformationContext {

  private val myProject: Project = myCodeClass.project
  private val myPsiManager: PsiManager = myCodeClass.manager
  private val myPsiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(myProject)
  private var myHierarchyView: PsiClass? = null
  private val myClassType: PsiClassType = myPsiFacade.elementFactory.createType(codeClass)
  private val myMemberBuilder = MemberBuilder(this)

  private val myMethods: LinkedList<PsiMethod> by lazy(LazyThreadSafetyMode.NONE) {
    myCodeClass.codeMethods.flatMapTo(LinkedList(), ::expandReflectedMethods)
  }
  private val myFields: MutableList<GrField> by lazy(LazyThreadSafetyMode.NONE) {
    myCodeClass.codeFields.toMutableList()
  }
  private val myInnerClasses: MutableList<PsiClass> by lazy(LazyThreadSafetyMode.NONE) {
    myCodeClass.codeInnerClasses.toMutableList<PsiClass>()
  }
  private val myImplementsTypes: MutableList<PsiClassType> by lazy(LazyThreadSafetyMode.NONE) {
    getReferenceListTypes(myCodeClass.implementsClause).toMutableList()
  }
  private val myExtendsTypes: MutableList<PsiClassType> by lazy(LazyThreadSafetyMode.NONE) {
    getReferenceListTypes(myCodeClass.extendsClause).toMutableList()
  }
  private val mySignaturesCache: Map<String, MutableSet<MethodSignature>> = FactoryMap.create { name ->
    val result = THashSet(METHOD_PARAMETERS_ERASURE_EQUALITY)
    for (existingMethod in myMethods) {
      if (existingMethod.name == name) {
        result.add(existingMethod.getSignature(PsiSubstitutor.EMPTY))
      }
    }
    result
  }

  override fun getCodeClass(): GrTypeDefinition = myCodeClass

  override fun getProject(): Project = myProject

  override fun getManager(): PsiManager = myPsiManager

  override fun getPsiFacade(): JavaPsiFacade = myPsiFacade

  override fun getHierarchyView(): PsiClass {
    val hierarchyView = myHierarchyView
    if (hierarchyView != null) {
      return hierarchyView
    }
    val newView = HierarchyView(
      myCodeClass,
      myExtendsTypes.toArray(PsiClassType.EMPTY_ARRAY),
      myImplementsTypes.toArray(PsiClassType.EMPTY_ARRAY),
      myPsiManager
    )
    myHierarchyView = newView
    return newView
  }

  override fun getClassType(): PsiClassType = myClassType

  override fun getMemberBuilder(): MemberBuilder = myMemberBuilder

  override fun getFields(): Collection<GrField> = myFields

  override fun getAllFields(includeSynthetic: Boolean): Collection<PsiField> {
    if (!includeSynthetic) {
      return getAllFields(codeClass, false).toList()
    }
    val fields = myFields.toMutableSet<PsiField>()
    val superTypes = myExtendsTypes + myImplementsTypes
    for (type in superTypes) {
      val psiClass = type.resolve() ?: continue
      fields += psiClass.allFields
    }
    return fields
  }

  override fun getMethods(): Collection<PsiMethod> = myMethods

  override fun getInnerClasses(): Collection<PsiClass> = myInnerClasses

  override fun getImplementsTypes(): List<PsiClassType> = myImplementsTypes

  override fun getExtendsTypes(): List<PsiClassType> = myExtendsTypes

  override fun getClassName(): String? = myCodeClass.name

  override fun getSuperClass(): PsiClass? = getSuperClass(codeClass, myExtendsTypes.toTypedArray())

  override fun getAnnotation(fqn: String): PsiAnnotation? = getAnnotation(codeClass, fqn)

  override fun isInheritor(baseClass: PsiClass): Boolean {
    if (manager.areElementsEquivalent(codeClass, baseClass)) return false
    if (codeClass.isInterface && !baseClass.isInterface) return false

    for (superType in superTypes) {
      val superClass = superType.resolve() ?: continue
      if (manager.areElementsEquivalent(superClass, baseClass)) return true
      if (superClass.isInheritor(baseClass, true)) return true
    }

    return false
  }

  override fun findMethodsByName(name: String, checkBases: Boolean): Collection<PsiMethod> {
    val methods = myMethods.filter {
      name == it.name
    }
    if (checkBases) {
      val superMethods = superClass?.findMethodsByName(name, true) ?: PsiMethod.EMPTY_ARRAY
      return methods + superMethods
    }
    else {
      return methods
    }
  }

  private fun doAddMethod(method: PsiMethod, prepend: Boolean) {
    if (method is GrLightMethodBuilder) {
      method.setContainingClass(myCodeClass)
    }
    else if (method is LightMethodBuilder) {
      @Suppress("UsePropertyAccessSyntax")
      method.setContainingClass(myCodeClass)
    }
    val signature = method.getSignature(PsiSubstitutor.EMPTY)
    val signatures = mySignaturesCache.getValue(method.name)
    if (signatures.add(signature)) {
      if (prepend) {
        myMethods.addFirst(method)
      }
      else {
        myMethods.addLast(method)
      }
    }
  }

  override fun addMethod(method: PsiMethod, prepend: Boolean) {
    for (expanded in expandReflectedMethods(method)) {
      doAddMethod(expanded, prepend)
    }
  }

  override fun addMethods(methods: Array<PsiMethod>) {
    for (method in methods) {
      addMethod(method)
    }
  }

  override fun addMethods(methods: Collection<PsiMethod>) {
    for (method in methods) {
      addMethod(method)
    }
  }

  override fun removeMethod(method: PsiMethod) {
    val signatures = mySignaturesCache.getValue(method.name)
    for (expanded in expandReflectedMethods(method)) {
      val signature = expanded.getSignature(PsiSubstitutor.EMPTY)
      if (signatures.remove(signature)) {
        myMethods.removeIf { m -> METHOD_PARAMETERS_ERASURE_EQUALITY.equals(signature, m.getSignature(PsiSubstitutor.EMPTY)) }
      }
    }
  }

  override fun addField(field: GrField) {
    if (field is GrLightField) {
      field.setContainingClass(codeClass)
    }
    myFields.add(field)
  }

  override fun addInnerClass(innerClass: PsiClass) {
    if (innerClass is LightPsiClassBuilder) {
      innerClass.containingClass = codeClass
    }
    myInnerClasses.add(innerClass)
  }

  override fun setSuperType(fqn: String) = setSuperType(createType(fqn, codeClass))

  override fun setSuperType(type: PsiClassType) {
    if (!codeClass.isInterface) {
      myExtendsTypes.clear()
      myExtendsTypes.add(type)
      myHierarchyView = null
    }
  }

  override fun addInterface(fqn: String) = addInterface(createType(fqn, codeClass))

  override fun addInterface(type: PsiClassType) {
    (if (!codeClass.isInterface || codeClass.isTrait) myImplementsTypes else myExtendsTypes).add(type)
    myHierarchyView = null
  }

  internal val transformationResult: TransformationResult
    get() = TransformationResult(
      (methods + enumMethods()).toArray(PsiMethod.EMPTY_ARRAY),
      fields.toArray(GrField.EMPTY_ARRAY),
      innerClasses.toArray(PsiClass.EMPTY_ARRAY),
      implementsTypes.toArray(PsiClassType.EMPTY_ARRAY),
      extendsTypes.toArray(PsiClassType.EMPTY_ARRAY)
    )

  private fun enumMethods() : List<PsiMethod> =
    if (myCodeClass is GrEnumTypeDefinitionImpl) myCodeClass.defEnumMethods else emptyList()
}
