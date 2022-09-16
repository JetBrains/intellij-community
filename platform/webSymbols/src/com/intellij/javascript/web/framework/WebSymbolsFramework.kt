// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javascript.web.framework

import com.intellij.javascript.web.context.findWebSymbolsFrameworkInContext
import com.intellij.javascript.web.symbols.SymbolKind
import com.intellij.javascript.web.symbols.WebSymbolNamesProvider
import com.intellij.javascript.web.symbols.WebSymbolsContainer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import javax.swing.Icon

abstract class WebSymbolsFramework {

  lateinit var id: String
    internal set

  abstract val displayName: String

  open val icon: Icon? get() = null

  open fun getNames(namespace: WebSymbolsContainer.Namespace,
                    kind: SymbolKind,
                    name: String,
                    target: WebSymbolNamesProvider.Target): List<String> = emptyList()

  fun isContext(context: PsiElement): Boolean = findWebSymbolsFrameworkInContext(context) == this

  fun isContext(context: VirtualFile, project: Project): Boolean = findWebSymbolsFrameworkInContext(context, project) == this


  companion object {

    private val WEB_SYMBOLS_FRAMEWORK_EP = object : KeyedExtensionCollector<WebSymbolsFramework, String>("com.intellij.javascript.web.framework") {
      val all get() = extensions.asSequence().map { it.instance }
    }

    @JvmStatic
    fun get(id: String): WebSymbolsFramework = WEB_SYMBOLS_FRAMEWORK_EP.findSingle(id) ?: UnregisteredWebSymbolsFramework(id)

    @JvmStatic
    fun forContext(context: VirtualFile, project: Project) = findWebSymbolsFrameworkInContext(context, project)

    @JvmStatic
    fun forContext(context: PsiElement) = findWebSymbolsFrameworkInContext(context)

    @JvmStatic
    val all: List<WebSymbolsFramework>
      get() = WEB_SYMBOLS_FRAMEWORK_EP.all.toList()

    @JvmStatic
    internal val allAsSequence: Sequence<WebSymbolsFramework>
      get() = WEB_SYMBOLS_FRAMEWORK_EP.all

  }

  private class UnregisteredWebSymbolsFramework(id: String) : WebSymbolsFramework() {
    init {
      this.id = id
    }

    override val displayName: String
      get() = id

  }

}