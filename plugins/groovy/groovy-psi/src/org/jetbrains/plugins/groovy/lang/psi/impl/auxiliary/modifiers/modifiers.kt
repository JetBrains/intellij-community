// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GrModifierListUtil")

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionImpl
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil.isInterface
import org.jetbrains.plugins.groovy.lang.psi.util.isCompactConstructor
import org.jetbrains.plugins.groovy.transformations.immutable.hasImmutableAnnotation

private val visibilityModifiers = setOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)
private const val explicitVisibilityModifiersMask = GrModifierFlags.PUBLIC_MASK or GrModifierFlags.PRIVATE_MASK or GrModifierFlags.PROTECTED_MASK
private const val packageScopeAnno = "groovy.transform.PackageScope"
private const val packageScopeTarget = "groovy.transform.PackageScopeTarget"

fun Int.hasMaskModifier(@GrModifierConstant @NonNls name: String): Boolean {
  return and(NAME_TO_MODIFIER_FLAG_MAP.getInt(name)) != 0
}

internal fun hasExplicitVisibilityModifiers(modifierList: GrModifierList): Boolean {
  return explicitVisibilityModifiersMask and modifierList.modifierFlags != 0
}

internal fun hasExplicitModifier(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String): Boolean {
  return modifierList.modifierFlags.hasMaskModifier(name)
}

@JvmOverloads
internal fun hasModifierProperty(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String, includeSynthetic: Boolean = true): Boolean {
  return hasExplicitModifier(modifierList, name) ||
         hasImplicitModifier(modifierList, name) ||
         (includeSynthetic && hasSyntheticModifier(modifierList, name))
}

private fun hasImplicitModifier(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String): Boolean {
  return when (name) {
    PsiModifier.ABSTRACT -> modifierList.isAbstract()
    PsiModifier.FINAL -> modifierList.isFinal()
    PsiModifier.STATIC -> modifierList.isStatic()
    else -> name in visibilityModifiers && name == getImplicitVisibility(modifierList)
  }
}

internal fun hasCodeModifierProperty(owner : PsiModifierListOwner, @GrModifierConstant @NonNls modifierName : String) : Boolean {
  return (owner.modifierList as? GrModifierList)?.let { hasModifierProperty(it, modifierName, false) } ?: false
}

private fun hasSyntheticModifier(modifierList: GrModifierList, name: String) : Boolean {
  val containingTypeDefinition = modifierList.parentOfType<GrTypeDefinition>() as? GrTypeDefinitionImpl ?: return false
  return containingTypeDefinition.getSyntheticModifiers(modifierList).contains(name)
}

private fun GrModifierList.isAbstract(): Boolean {
  return when (val owner = parent) {
    is GrMethod -> owner.isAbstractMethod()
    is GrTypeDefinition -> owner.isAbstractClass()
    else -> false
  }
}

private fun GrMethod.isAbstractMethod(): Boolean =
  containingClass?.let { it.isInterface && !it.isCodeTrait() && !this.modifierList.hasExplicitModifier(PsiModifier.DEFAULT) } ?: false

private fun PsiClass.isCodeTrait() : Boolean = this is GrTypeDefinition && this.isTrait

private fun GrTypeDefinition.isAbstractClass(): Boolean {
  if (isEnum) {
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(this, GroovyConfigUtils.GROOVY2_0)) {
      return codeMethods.any { it.modifierList.hasExplicitModifier(PsiModifier.ABSTRACT) }
    }
  }
  return isInterface
}

private fun GrModifierList.isFinal(): Boolean {
  return when (val owner = parent) {
    is GrTypeDefinition -> owner.isFinalClass()
    is GrVariableDeclaration -> owner.isFinalField(this)
    is GrEnumConstant -> true
    else -> false
  }
}

private fun GrTypeDefinition.isFinalClass(): Boolean {
  return this is GrRecordDefinition || (isEnum && codeFields.none { it is GrEnumConstant && it.initializingClass != null })
}

private fun GrVariableDeclaration.isFinalField(modifierList: GrModifierList): Boolean {
  val containingClass = containingClass
  return isInterface(containingClass)
         || !modifierList.hasExplicitVisibilityModifiers()
         && (containingClass?.let(::hasImmutableAnnotation) ?: false)
}

private fun GrModifierList.isStatic(): Boolean {
  val owner = parent
  val containingClass = when (owner) {
    is GrTypeDefinition -> owner.containingClass
    is GrVariableDeclaration -> owner.containingClass
    is GrEnumConstant -> return true
    else -> null
  }
  return containingClass != null && (owner is GrEnumTypeDefinition || isInterface(containingClass))
}

private fun getImplicitVisibility(grModifierList: GrModifierList): String? {
  if (hasExplicitVisibilityModifiers(grModifierList)) return null
  when (val owner = grModifierList.parent) {
    is GrTypeDefinition -> return if (grModifierList.hasPackageScope(owner, "CLASS")) PsiModifier.PACKAGE_LOCAL else PsiModifier.PUBLIC
    is GrMethod -> {
      val containingClass = owner.containingClass as? GrTypeDefinition ?: return null
      if (isInterface(containingClass)) return PsiModifier.PUBLIC
      if (containingClass is GrRecordDefinition && owner.isCompactConstructor()) return null
      val targetName = if (owner.isConstructor) "CONSTRUCTORS" else "METHODS"
      return if (grModifierList.hasPackageScope(containingClass, targetName)) PsiModifier.PACKAGE_LOCAL else PsiModifier.PUBLIC
    }
    is GrVariableDeclaration -> {
      val containingClass = owner.containingClass ?: return null
      if (isInterface(containingClass)) return PsiModifier.PUBLIC
      return if (grModifierList.hasPackageScope(containingClass, "FIELDS")) PsiModifier.PACKAGE_LOCAL else PsiModifier.PRIVATE
    }
    is GrEnumConstant -> return PsiModifier.PUBLIC
    else -> return null
  }
}

private fun GrModifierList.hasPackageScope(clazz: GrTypeDefinition?, targetName: String): Boolean {
  if (hasOwnEmptyPackageScopeAnnotation()) return true

  val annotation = clazz?.modifierList?.findAnnotation(packageScopeAnno) as? GrAnnotation ?: return false
  val value = annotation.findDeclaredDetachedValue(null) ?: return false // annotation without value

  val scopeTargetEnum = JavaPsiFacade.getInstance(project).findClass(packageScopeTarget, resolveScope) ?: return false
  val scopeTarget = scopeTargetEnum.findFieldByName(targetName, false) ?: return false

  val resolved = when (value) {
    is GrReferenceExpression -> value.resolve()?.let { listOf(it) } ?: emptyList()
    is GrAnnotationArrayInitializer -> value.initializers.mapNotNull {
      (it as? GrReferenceExpression)?.resolve()
    }
    else -> emptyList()
  }

  return scopeTarget in resolved
}

private fun GrModifierList.hasOwnEmptyPackageScopeAnnotation(): Boolean {
  val annotation = findAnnotation(packageScopeAnno) ?: return false
  val value = annotation.findDeclaredDetachedValue(null) ?: return true
  return value is GrAnnotationArrayInitializer && value.initializers.isEmpty()
}

private val GrVariableDeclaration.containingClass get() = (parent as? GrTypeDefinitionBody)?.parent as? GrTypeDefinition
