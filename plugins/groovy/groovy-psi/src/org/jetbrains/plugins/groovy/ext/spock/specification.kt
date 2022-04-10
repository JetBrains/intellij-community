// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Ref
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getContextClass

fun GroovyPsiElement.isInsideSpecification(): Boolean {
  val clazz = getContextClass(this) ?: return false
  val ref = Ref(false)
  DumbService.getInstance(clazz.project).runWithAlternativeResolveEnabled<Throwable> {
    ref.set(isInheritor(clazz, false, SpockUtils.SPEC_CLASS_NAME))
  }
  return ref.get()
}
