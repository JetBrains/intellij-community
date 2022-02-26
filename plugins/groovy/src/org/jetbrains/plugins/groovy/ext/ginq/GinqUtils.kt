// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GinqUtils")

package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

const val GROOVY_GINQ_TRANSFORM_GQ = "groovy.ginq.transform.GQ"

val ginqMethods: Set<String> = setOf(
  "GQ",
  "GQL",
)

val qualifiedGinqMethods: Set<String> = ginqMethods.mapTo(HashSet()) { "org.apache.groovy.ginq.$it" }

fun isGinqAvailable(context: PsiElement): Boolean {
  val module = ModuleUtil.findModuleForPsiElement(context) ?: return false
  return JavaPsiFacade.getInstance(context.project)
    .findClass(GROOVY_GINQ_TRANSFORM_GQ, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null
}