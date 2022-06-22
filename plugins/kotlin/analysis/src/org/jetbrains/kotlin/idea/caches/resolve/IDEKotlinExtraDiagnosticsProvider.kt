// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.KotlinExtraDiagnosticsProvider
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

class IDEKotlinExtraDiagnosticsProvider: KotlinExtraDiagnosticsProvider {
    override fun forClassOrObject(kclass: KtClassOrObject): Diagnostics = Diagnostics.EMPTY
    override fun forFacade(file: KtFile, moduleScope: GlobalSearchScope): Diagnostics = Diagnostics.EMPTY
}