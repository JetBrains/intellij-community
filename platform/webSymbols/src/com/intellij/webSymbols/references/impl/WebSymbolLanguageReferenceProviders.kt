// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references.impl

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.util.SmartList
import java.util.concurrent.ConcurrentHashMap

internal class WebSymbolLanguageReferenceProviders(private val myBeans: MutableList<PsiWebSymbolReferenceProviderBean>) {
  private val myBeansByHostClass = ConcurrentHashMap<Class<*>, List<PsiWebSymbolReferenceProviderBean>>()

  fun byHostClass(aClass: Class<out PsiExternalReferenceHost>): List<PsiWebSymbolReferenceProviderBean> {
    return myBeansByHostClass.computeIfAbsent(aClass) { key -> this.byHostClassInner(key) }
  }

  private fun byHostClassInner(key: Class<*>): List<PsiWebSymbolReferenceProviderBean> {
    val result = SmartList<PsiWebSymbolReferenceProviderBean>()
    for (bean in myBeans) {
      if (bean.getHostElementClass().isAssignableFrom(key)) {
        result.add(bean)
      }
    }
    return result
  }
}
