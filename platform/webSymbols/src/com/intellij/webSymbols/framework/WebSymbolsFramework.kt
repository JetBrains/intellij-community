// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import javax.swing.Icon

abstract class WebSymbolsFramework {

  lateinit var id: String
    internal set

  abstract val displayName: String

  open val icon: Icon?
    get() = null

  open fun getNames(namespace: SymbolNamespace,
                    kind: SymbolKind,
                    name: String,
                    target: WebSymbolNamesProvider.Target): List<String> = emptyList()

  fun isInContext(location: PsiElement): Boolean = WebSymbolsContext.get(KIND_FRAMEWORK, location) == id

  fun isInContext(location: VirtualFile, project: Project): Boolean = WebSymbolsContext.get(KIND_FRAMEWORK, location, project) == id

  companion object {

    private val WEB_FRAMEWORK_EP = object : KeyedExtensionCollector<WebSymbolsFramework, String>("com.intellij.webSymbols.framework") {
      val all get() = extensions.asSequence().map { it.instance }
    }

    @JvmStatic
    fun get(id: String): WebSymbolsFramework = WEB_FRAMEWORK_EP.findSingle(id) ?: UnregisteredWebFramework(id)

    @JvmStatic
    fun inLocation(location: VirtualFile, project: Project) = WebSymbolsContext.get(KIND_FRAMEWORK, location, project)?.let { get(it) }

    @JvmStatic
    fun inLocation(location: PsiElement) = WebSymbolsContext.get(KIND_FRAMEWORK, location)?.let { get(it) }

    @JvmStatic
    val all: List<WebSymbolsFramework>
      get() = WEB_FRAMEWORK_EP.all.toList()

    @JvmStatic
    internal val allAsSequence: Sequence<WebSymbolsFramework>
      get() = WEB_FRAMEWORK_EP.all

  }

  private class UnregisteredWebFramework(id: String) : WebSymbolsFramework() {
    init {
      this.id = id
    }

    override val displayName: String
      get() = id

  }

}