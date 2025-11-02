// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.driver

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.driver.REMOTE_ANNOTATION_FQN
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElementOfType
import javax.swing.Icon

internal class RemoteIconProvider : IconProvider() {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (element is PsiClass
        && element.isInterface
        && AnnotationUtil.isAnnotated(element, REMOTE_ANNOTATION_FQN, 0)) {
      return DevkitCoreIcons.RemoteMapping
    }

    if (element is KtClassOrObject && element.isInterfaceClass()) {
      val uCLass = element.toUElementOfType<UClass>()
      val javaPsi = uCLass?.javaPsi
      if (javaPsi != null && AnnotationUtil.isAnnotated(javaPsi, REMOTE_ANNOTATION_FQN, 0)) {
        return DevkitCoreIcons.RemoteMapping
      }
    }

    return null
  }
}