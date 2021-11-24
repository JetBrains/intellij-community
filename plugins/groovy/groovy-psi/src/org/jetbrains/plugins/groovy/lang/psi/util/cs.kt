// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CompileStaticUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isEnumConstant

fun isCompileStatic(e: PsiElement): Boolean {
  val containingMember = PsiTreeUtil.getParentOfType(e, PsiMember::class.java, false)
  return containingMember != null && isCompileStatic(containingMember)
}

/**
 * Returns `@POJO` annotation or `null` if this annotation is absent or has no effect
 */
fun getPOJO(typedef : GrTypeDefinition): PsiAnnotation? {
  // we are interested in static compilation here, not in static typechecker
  val inCSContext = typedef.parentsOfType<GrMember>(true).any { it.hasAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC) }
  if (inCSContext) {
    return typedef.getAnnotation(GROOVY_TRANSFORM_STC_POJO)
  } else {
    return null
  }
}

fun isCompileStatic(member: PsiMember): Boolean {
  return CachedValuesManager.getProjectPsiDependentCache(member, ::isCompileStaticInner)
}

private fun isCompileStaticInner(member: PsiMember): Boolean {
  val annotation = getCompileStaticAnnotation(member)
  if (annotation != null) return checkForPass(annotation)
  val enclosingMember = PsiTreeUtil.getParentOfType(member, PsiMember::class.java, true)
  return enclosingMember != null && isCompileStatic(enclosingMember)
}

fun getCompileStaticAnnotation(member: PsiMember): PsiAnnotation? {
  val list = member.modifierList ?: return null
  return list.findAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC)
         ?: list.findAnnotation(GROOVY_TRANSFORM_TYPE_CHECKED)
}

fun checkForPass(annotation: PsiAnnotation): Boolean {
  val value = annotation.findAttributeValue("value")
  return value == null || value is PsiReference && isEnumConstant(value, "PASS", GROOVY_TRANSFORM_TYPE_CHECKING_MODE)
}
