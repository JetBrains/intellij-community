/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


@file:JvmName("GrModifierListUtil")

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers

import com.intellij.psi.PsiModifier
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.*
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil

private val explicitVisibilityModifiers = PUBLIC_MASK or PRIVATE_MASK or PROTECTED_MASK

fun Int.hasMaskModifier(@GrModifierConstant @NonNls name: String): Boolean {
  return and(NAME_TO_MODIFIER_FLAG_MAP[name]) != 0
}

internal fun GrModifierList.hasExplicitVisibilityModifiers(): Boolean {
  return explicitVisibilityModifiers and modifierFlags != 0
}

internal fun GrModifierList.hasExplicitModifier(@GrModifierConstant @NonNls name: String): Boolean {
  return modifierFlags.hasMaskModifier(name)
}

internal fun GrModifierList.hasModifierProperty(@GrModifierConstant @NonNls name: String): Boolean {
  return getAllModifierFlags().hasMaskModifier(name)
}

internal fun GrModifierList.getAllModifierFlags(): Int = CachedValuesManager.getCachedValue(this) {
  Result.create(doGetModifierFlags(), if (isPhysical) this else PsiModificationTracker.MODIFICATION_COUNT)
}

private fun GrModifierList.doGetModifierFlags(): Int {
  return modifierFlags or parent.let { owner ->
    if (owner is GrTypeDefinition) {
      doGetTypeDefinitionFlags(owner, this)
    }
    else {
      val parent = PsiTreeUtil.getStubOrPsiParent(owner) as? GrTypeDefinitionBody
      val containingClass = parent?.parent as? GrTypeDefinition
      when (owner) {
        is GrMethod -> doGetMethodFlags(containingClass, this)
        is GrVariableDeclaration -> doGetVariableModifierMask(containingClass, this)
        else -> 0
      }
    }
  }
}

private fun doGetTypeDefinitionFlags(clazz: GrTypeDefinition, modifierList: GrModifierList): Int {
  var flags = 0

  if (clazz.isInterface) {
    flags = flags or ABSTRACT_MASK
  }
  else if (clazz.isEnum) {
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(modifierList, GroovyConfigUtils.GROOVY2_0)) {
      if (clazz.codeMethods.any { it.hasModifierProperty(PsiModifier.ABSTRACT) }) {
        flags = flags or ABSTRACT_MASK
      }
    }
    if (clazz.codeFields.none { it is GrEnumConstant && it.initializingClass != null }) {
      flags = flags or FINAL_MASK
    }
  }

  val containingClass = clazz.containingClass
  if (GrTraitUtil.isInterface(containingClass)) {
    flags = flags or STATIC_MASK
  }

  if (!modifierList.hasExplicitVisibilityModifiers()) {
    flags = flags or PUBLIC_MASK
  }

  return flags
}

private fun doGetMethodFlags(clazz: GrTypeDefinition?, modifierList: GrModifierList): Int {
  var flags = 0

  if (clazz != null) {
    if (clazz.isInterface) flags = flags or ABSTRACT_MASK // groovy interface or trait
    if (GrTraitUtil.isInterface(clazz)) flags = flags or PUBLIC_MASK // groovy interface
  }

  if (!modifierList.hasExplicitVisibilityModifiers()) {
    flags = flags or PUBLIC_MASK
  }

  return flags
}

private fun doGetVariableModifierMask(clazz: GrTypeDefinition?, modifierList: GrModifierList): Int {
  var flags = 0

  if (clazz != null) {
    if (GrTraitUtil.isInterface(clazz)) {
      flags = flags or STATIC_MASK or FINAL_MASK or PUBLIC_MASK
    }
    else if (!modifierList.hasExplicitVisibilityModifiers()) {
      flags = flags or PRIVATE_MASK
      if (clazz.modifierList?.let { PsiImplUtil.hasImmutableAnnotation(it) } == true) {
        flags = flags or FINAL_MASK
      }
    }
  }

  return flags
}