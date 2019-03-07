// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getContextClass

fun GroovyPsiElement.isInsideSpecification(): Boolean {
  val clazz = getContextClass(this)
  return isInheritor(clazz, false, SpockUtils.SPEC_CLASS_NAME)
}
