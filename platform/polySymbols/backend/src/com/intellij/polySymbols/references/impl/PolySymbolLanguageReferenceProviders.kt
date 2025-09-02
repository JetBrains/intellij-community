// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.util.SmartList
import java.util.concurrent.ConcurrentHashMap

internal class PolySymbolLanguageReferenceProviders(private val myBeans: MutableList<PsiPolySymbolReferenceProviderBean>) {
  private val myBeansByHostClass = ConcurrentHashMap<Class<*>, List<PsiPolySymbolReferenceProviderBean>>()

  fun byHostClass(aClass: Class<out PsiExternalReferenceHost>): List<PsiPolySymbolReferenceProviderBean> {
    return myBeansByHostClass.computeIfAbsent(aClass) { key -> this.byHostClassInner(key) }
  }

  private fun byHostClassInner(key: Class<*>): List<PsiPolySymbolReferenceProviderBean> {
    val result = SmartList<PsiPolySymbolReferenceProviderBean>()
    for (bean in myBeans) {
      if (bean.getHostElementClass().isAssignableFrom(key)) {
        result.add(bean)
      }
    }
    return result
  }
}
