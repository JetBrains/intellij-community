// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getContextClass

fun GroovyPsiElement.isInsideSpecification(): Boolean {
  val clazz = getContextClass(this) ?: return false
  return DumbService.getInstance(clazz.project).computeWithAlternativeResolveEnabled<Boolean, Throwable> {
    var defaultProjectValue: Boolean? = null
    if (project.isDefault) {
      defaultProjectValue = clazz.superClass?.qualifiedName?.equals(SpockUtils.SPEC_CLASS_NAME)
    }
    defaultProjectValue ?: clazz.isSpockSpecification()
  }
}

fun PsiClass.isSpockSpecification() : Boolean = isInheritor(this, false, SpockUtils.SPEC_CLASS_NAME)