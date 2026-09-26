// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.driver

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

internal const val REMOTE_ANNOTATION_FQN: String = "com.intellij.driver.client.Remote"

internal class RemoteUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    val project = element.project
    if (!JavaLibraryUtil.hasLibraryClass(project, REMOTE_ANNOTATION_FQN)) return null

    val uMethod = element.toUElementOfType<UMethod>()
    if (uMethod != null
        && (uMethod.javaPsi.hasModifier(JvmModifier.PRIVATE)
            || uMethod.javaPsi.hasModifier(JvmModifier.PACKAGE_LOCAL)
            || uMethod.javaPsi.hasModifier(JvmModifier.PROTECTED))
    ) {
      return null
    }

    val uClass = element.toUElementOfType<UClass>()
    if (uClass == null && uMethod == null) return null

    val remoteClass = JavaPsiFacade.getInstance(project)
                        .findClass(REMOTE_ANNOTATION_FQN, allScope(project)) ?: return null

    // only search in project sources; when annotation comes from libraries, it may have a huge .useScope
    return remoteClass.useScope.intersectWith(GlobalSearchScope.projectScope(project))
  }
}