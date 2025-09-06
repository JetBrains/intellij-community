// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

class GradleConfigureExtensionMethod(manager: PsiManager, name: String, val parentKey: String, extensionType: GradleExtensionType, containingClass: PsiClass) : GrLightMethodBuilder(manager, name) {
  init {
    this.returnType = extensionType
    this.containingClass = containingClass;
    val resultType = extensionType.resolve()
    if (resultType != null) {
      navigationElement = resultType
    }

    addAndGetParameter("configuration", createType(GROOVY_LANG_CLOSURE, containingClass))
      .putUserData(DELEGATES_TO_KEY, DelegatesToInfo(extensionType, Closure.DELEGATE_FIRST))
  }
}