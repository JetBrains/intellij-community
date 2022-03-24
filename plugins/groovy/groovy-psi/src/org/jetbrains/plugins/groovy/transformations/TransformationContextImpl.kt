// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.light.LightPsiClassBuilder
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.FactoryMap
import com.intellij.util.containers.toArray
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.hasCodeModifierProperty
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.hasModifierProperty
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
  private val myClassType: PsiClassType = myPsiFacade.elementFactory.createType(codeClass, PsiSubstitutor.EMPTY)
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
  private val myModifiers: MutableMap<GrModifierList, MutableList<String>> = mutableMapOf()
  private val mySignaturesCache: Map<String, MutableSet<MethodSignature>> = FactoryMap.create { name ->
    val result = ObjectOpenCustomHashSet(AST_TRANSFORMATION_AWARE_METHOD_PARAMETERS_ERASURE_EQUALITY)
    for (existingMethod in myMethods) {
      if (existingMethod.name == name) {
        result.add(existingMethod.getSignature(PsiSubstitutor.EMPTY))
      }
    }
    result
  }
  private val myAnnotations: MutableList<PsiAnnotation> by lazy(LazyThreadSafetyMode.NONE) {
    myCodeClass.annotations.toMutableList()
  }

  @Suppress("ClassName")
  // Modifiers should be processed with care in transformation context to avoid recursion issues.
  // This code re-creates erasures computation to properly handle modifier querying
  private val AST_TRANSFORMATION_AWARE_METHOD_PARAMETERS_ERASURE_EQUALITY: Hash.Strategy<MethodSignature> = object : Hash.Strategy<MethodSignature> {
    override fun equals(a: MethodSignature?, b: MethodSignature?): Boolean {
      if (a === b) return true
      return (a != null && b != null && a.parameterTypes.map { erase(it) } == b.parameterTypes.map { erase(it) })
    }

    override fun hashCode(signature: MethodSignature?): Int {
      return signature?.name?.hashCode() ?: 0
    }
  }

  private fun erase(type: PsiType): PsiType? = type.accept(object : PsiTypeVisitor<PsiType?>() {

    override fun visitType(type: PsiType): PsiType = type

    override fun visitArrayType(arrayType: PsiArrayType): PsiType? = arrayType.componentType.accept(this)?.createArrayType()

    override fun visitClassType(classType: PsiClassType): PsiType = eraseClassType(classType)

    override fun visitDisjunctionType(disjunctionType: PsiDisjunctionType): PsiType? {
      val lub = PsiTypesUtil.getLowestUpperBoundClassType(disjunctionType)
      return lub?.run { eraseClassType(this) } ?: lub
    }
  })

  override fun eraseClassType(type: PsiClassType): PsiClassType {
    val factory = JavaPsiFacade.getElementFactory(project)

    val clazz: PsiClass? = type.resolve()
    return if (clazz != null) {
      val erasureSubstitutor = PsiSubstitutor.createSubstitutor(typeParameters(clazz).map { it to null }.toMap())
      factory.createType(clazz, erasureSubstitutor, type.languageLevel)
    }
    else {
      return type
    }
  }

  private fun typeParameters(owner: PsiTypeParameterListOwner): List<PsiTypeParameter?> {
    val result: MutableList<PsiTypeParameter?> = mutableListOf()
    var currentOwner: PsiTypeParameterListOwner? = owner
    while (currentOwner != null) {
      val typeParameters = currentOwner.typeParameters
      result.addAll(typeParameters)
      val modifierList = currentOwner.modifierList
      if (modifierList is GrModifierList && hasModifierProperty(modifierList, PsiModifier.STATIC)) break
      else if (modifierList != null && hasCodeModifierProperty(currentOwner, PsiModifier.STATIC)) break
      currentOwner = currentOwner.containingClass
    }
    return result
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

  override fun hasModifierProperty(list: GrModifierList, name: String): Boolean =
    hasModifierProperty(list, name, false) || myModifiers.getOrDefault(list, emptyList()).contains(name)

  override fun getClassName(): String? = myCodeClass.name

  override fun getSuperClass(): PsiClass? = getSuperClass(codeClass, myExtendsTypes.toTypedArray())

  override fun getAnnotation(fqn: String): PsiAnnotation? = myAnnotations.find { it.qualifiedName == fqn }

  override fun addAnnotation(annotation: GrAnnotation) {
    myAnnotations.add(annotation)
  }

  override fun isInheritor(baseClass: PsiClass): Boolean {
    if (manager.areElementsEquivalent(codeClass, baseClass)) return true
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
        myMethods.removeIf { m -> com.intellij.psi.util.MethodSignatureUtil.areSignaturesErasureEqual(signature, m.getSignature(PsiSubstitutor.EMPTY)) }
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

  override fun addModifier(modifierList: GrModifierList, modifier: String) {
    myModifiers.computeIfAbsent(modifierList) { mutableListOf() }.add(modifier)
  }

  internal val transformationResult: TransformationResult
    get() = TransformationResult(
      (methods + enumMethods()).toArray(PsiMethod.EMPTY_ARRAY),
      fields.toArray(GrField.EMPTY_ARRAY),
      innerClasses.toArray(PsiClass.EMPTY_ARRAY),
      implementsTypes.toArray(PsiClassType.EMPTY_ARRAY),
      extendsTypes.toArray(PsiClassType.EMPTY_ARRAY),
      myModifiers
    )

  private fun enumMethods() : List<PsiMethod> {
    return if (myCodeClass is GrEnumTypeDefinitionImpl) myCodeClass.getDefEnumMethods(this) else emptyList()
  }
}
