// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.withPrevious
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.util.contexts

fun isAccessible(member: PsiMember, place: PsiElement): Boolean {
  if (isInGroovyDoc(place)) {
    return true
  }
  val modifierList = member.modifierList
  if (modifierList == null) {
    return true
  }
  return when (PsiUtil.getAccessLevel(modifierList)) {
    PsiUtil.ACCESS_LEVEL_PUBLIC -> true
    PsiUtil.ACCESS_LEVEL_PROTECTED -> isProtectedMemberAccessibleFrom(place, member)
    PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL -> isPackageLocalMemberAccessibleFrom(place, member)
    PsiUtil.ACCESS_LEVEL_PRIVATE -> isPrivateMemberAccessibleFrom(member, place)
    else -> error("unexpected access level")
  }
}

private fun isProtectedMemberAccessibleFrom(place: PsiElement, member: PsiMember): Boolean {
  if (JavaPsiFacade.getInstance(place.project).arePackagesTheSame(member, place)) {
    return true
  }
  val memberClass = member.containingClass ?: return false
  for (contextClass in place.contexts().filterIsInstance<GrTypeDefinition>()) {
    if (InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
      return true
    }
  }
  return false
}

private fun isPackageLocalMemberAccessibleFrom(place: PsiElement, member: PsiMember): Boolean {
  val facade = JavaPsiFacade.getInstance(place.project)
  if (!facade.arePackagesTheSame(member, place)) {
    return false
  }
  val memberClass: PsiClass = member.containingClass ?: return true
  val placeClass: GrTypeDefinition = getContextClass(place) ?: return true
  val placeSuperClass: PsiClass = placeClass.superClass ?: return true
  if (!placeSuperClass.isInheritor(memberClass, true)) {
    return true
  }
  for (superClass in generateSequence(placeSuperClass, PsiClass::getSuperClass)) {
    if (superClass == memberClass) {
      return true
    }
    if (!facade.arePackagesTheSame(superClass, memberClass)) {
      return false
    }
  }
  return false
}

private fun isPrivateMemberAccessibleFrom(member: PsiMember, place: PsiElement): Boolean {
  val memberClass: GrTypeDefinition = member.containingClass as? GrTypeDefinition ?: return false
  if (memberClass is GroovyScriptClass) {
    for ((context, previousContext) in place.contexts().withPrevious()) {
      if (context is GroovyFile && previousContext !is GrTypeDefinition) {
        return context.scriptClass == memberClass
      }
    }
    return false
  }
  else {
    val memberTopLevelClass: GrTypeDefinition? = memberClass.contexts().filterIsInstance<GrTypeDefinition>().lastOrNull()
    val placeTopLevelClass: GrTypeDefinition? = place.contexts().filterIsInstance<GrTypeDefinition>().lastOrNull()
    return placeTopLevelClass == memberTopLevelClass
  }
}

private fun isInGroovyDoc(place: PsiElement): Boolean = place.parentOfType<GrDocComment>() != null

private fun getContextClass(place: PsiElement): GrTypeDefinition? {
  return place.contexts().filterIsInstance<GrTypeDefinition>().firstOrNull()
}
