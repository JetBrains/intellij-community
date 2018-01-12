/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("GrModifierListUtil")

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil.isInterface

private val visibilityModifiers = setOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)
private val explicitVisibilityModifiersMask = GrModifierFlags.PUBLIC_MASK or GrModifierFlags.PRIVATE_MASK or GrModifierFlags.PROTECTED_MASK
private val packageScopeAnno = "groovy.transform.PackageScope"
private val packageScopeTarget = "groovy.transform.PackageScopeTarget"

fun Int.hasMaskModifier(@GrModifierConstant @NonNls name: String): Boolean {
  return and(NAME_TO_MODIFIER_FLAG_MAP[name]) != 0
}

internal fun hasExplicitVisibilityModifiers(modifierList: GrModifierList): Boolean {
  return explicitVisibilityModifiersMask and modifierList.modifierFlags != 0
}

internal fun hasExplicitModifier(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String): Boolean {
  return modifierList.modifierFlags.hasMaskModifier(name)
}

internal fun hasModifierProperty(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String): Boolean {
  return hasExplicitModifier(modifierList, name) || hasImplicitModifier(modifierList, name)
}

private fun hasImplicitModifier(modifierList: GrModifierList, @GrModifierConstant @NonNls name: String): Boolean {
  return when (name) {
    PsiModifier.ABSTRACT -> modifierList.isAbstract()
    PsiModifier.FINAL -> modifierList.isFinal()
    PsiModifier.STATIC -> modifierList.isStatic()
    else -> name in visibilityModifiers && name == modifierList.getImplicitVisiblity()
  }
}

private fun GrModifierList.isAbstract(): Boolean {
  val owner = parent
  return when (owner) {
    is GrMethod -> owner.isAbsractMethod()
    is GrTypeDefinition -> owner.isAbstractClass()
    else -> false
  }
}

private fun GrMethod.isAbsractMethod(): Boolean = containingClass?.isInterface ?: false

private fun GrTypeDefinition.isAbstractClass(): Boolean {
  if (isEnum) {
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(this, GroovyConfigUtils.GROOVY2_0)) {
      return codeMethods.any { it.modifierList.hasExplicitModifier(PsiModifier.ABSTRACT) }
    }
  }
  return isInterface
}

private fun GrModifierList.isFinal(): Boolean {
  val owner = parent
  return when (owner) {
    is GrTypeDefinition -> owner.isFinalClass()
    is GrVariableDeclaration -> owner.isFinalField(this)
    else -> false
  }
}

private fun GrTypeDefinition.isFinalClass(): Boolean {
  return isEnum && codeFields.none { it is GrEnumConstant && it.initializingClass != null }
}

private fun GrVariableDeclaration.isFinalField(modifierList: GrModifierList): Boolean {
  val containingClass = containingClass
  return isInterface(containingClass)
         || !modifierList.hasExplicitVisibilityModifiers()
         && (containingClass?.modifierList?.let(PsiImplUtil::hasImmutableAnnotation) ?: false)
}

private fun GrModifierList.isStatic(): Boolean {
  val owner = parent
  val containingClass = when (owner) {
    is GrTypeDefinition -> owner.containingClass
    is GrVariableDeclaration -> owner.containingClass
    else -> null
  }
  return containingClass != null && (owner is GrEnumTypeDefinition || isInterface(containingClass))
}

private fun GrModifierList.getImplicitVisiblity(): String? {
  if (hasExplicitVisibilityModifiers(this)) return null
  val owner = parent
  when (owner) {
    is GrTypeDefinition -> return if (hasPackageScope(owner, "CLASS")) PsiModifier.PACKAGE_LOCAL else PsiModifier.PUBLIC
    is GrMethod -> {
      val containingClass = owner.containingClass as? GrTypeDefinition ?: return null
      if (isInterface(containingClass)) return PsiModifier.PUBLIC
      val targetName = if (owner.isConstructor) "CONSTRUCTORS" else "METHODS"
      return if (hasPackageScope(containingClass, targetName)) PsiModifier.PACKAGE_LOCAL else PsiModifier.PUBLIC
    }
    is GrVariableDeclaration -> {
      val containingClass = owner.containingClass ?: return null
      if (isInterface(containingClass)) return PsiModifier.PUBLIC
      return if (hasPackageScope(containingClass, "FIELDS")) PsiModifier.PACKAGE_LOCAL else PsiModifier.PRIVATE
    }
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
