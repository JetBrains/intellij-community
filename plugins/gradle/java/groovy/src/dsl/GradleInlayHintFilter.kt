// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.dsl

import com.intellij.gradle.java.groovy.service.resolve.isGradleScript
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.editor.GroovyInlayHintFilter

class GradleInlayHintFilter : GroovyInlayHintFilter {

  override fun shouldHideHints(element: PsiElement): Boolean {
    return element.containingFile.isGradleScript()
  }
}