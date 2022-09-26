// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbolNamesProvider
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.framework.impl.findWebSymbolsFrameworkInContext
import javax.swing.Icon

abstract class WebFramework {

  lateinit var id: String
    internal set

  abstract val displayName: String

  open val icon: Icon? get() = null

  open fun getNames(namespace: SymbolNamespace,
                    kind: SymbolKind,
                    name: String,
                    target: WebSymbolNamesProvider.Target): List<String> = emptyList()

  fun isContext(context: PsiElement): Boolean = findWebSymbolsFrameworkInContext(context) == this

  fun isContext(context: VirtualFile, project: Project): Boolean = findWebSymbolsFrameworkInContext(context, project) == this

  companion object {

    private val WEB_FRAMEWORK_EP = object : KeyedExtensionCollector<WebFramework, String>("com.intellij.webSymbols.framework") {
      val all get() = extensions.asSequence().map { it.instance }
    }

    @JvmStatic
    fun get(id: String): WebFramework = WEB_FRAMEWORK_EP.findSingle(id) ?: UnregisteredWebFramework(id)

    @JvmStatic
    fun forContext(context: VirtualFile, project: Project) = findWebSymbolsFrameworkInContext(context, project)

    @JvmStatic
    fun forContext(context: PsiElement) = findWebSymbolsFrameworkInContext(context)

    @JvmStatic
    val all: List<WebFramework>
      get() = WEB_FRAMEWORK_EP.all.toList()

    @JvmStatic
    internal val allAsSequence: Sequence<WebFramework>
      get() = WEB_FRAMEWORK_EP.all

  }

  private class UnregisteredWebFramework(id: String) : WebFramework() {
    init {
      this.id = id
    }

    override val displayName: String
      get() = id

  }

}