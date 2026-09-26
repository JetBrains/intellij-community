// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("EditorUtils")

package org.jetbrains.plugins.groovy.editor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

private val inlayHintFilterEp: ExtensionPointName<GroovyInlayHintFilter> = ExtensionPointName.create("org.intellij.groovy.inlayHintFilter")

fun shouldHideInlayHints(element: PsiElement): Boolean {
  return inlayHintFilterEp.extensionList.any {
    it.shouldHideHints(element)
  }
}
