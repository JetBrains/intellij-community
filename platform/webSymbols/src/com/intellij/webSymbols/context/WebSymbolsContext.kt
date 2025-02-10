// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.context.impl.WebSymbolsContextImpl
import com.intellij.webSymbols.context.impl.WebSymbolsContextProviderExtensionCollector
import com.intellij.webSymbols.context.impl.findWebSymbolsContext
import org.jetbrains.annotations.TestOnly

interface WebSymbolsContext {

  val framework: FrameworkId?
    get() = this[KIND_FRAMEWORK]

  operator fun get(kind: ContextKind): ContextName?

  @Suppress("MayBeConstant")
  companion object {

    @TestOnly
    @JvmField
    val WEB_SYMBOLS_CONTEXT_EP: KeyedExtensionCollector<WebSymbolsContextProvider, String> =
      WebSymbolsContextProviderExtensionCollector(ExtensionPointName("com.intellij.webSymbols.context"))

    @JvmField
    val KIND_FRAMEWORK: String = "framework"

    @JvmField
    val VALUE_NONE: String = "none"

    @JvmField
    val WEB_SYMBOLS_CONTEXT_FILE: String = ".ws-context"

    @JvmField
    val PKG_MANAGER_NODE_PACKAGES: String = "node-packages"

    @JvmField
    val PKG_MANAGER_RUBY_GEMS: String = "ruby-gems"

    @JvmField
    val PKG_MANAGER_SYMFONY_BUNDLES: String = "symfony-bundles"

    @JvmStatic
    fun get(kind: ContextKind, location: VirtualFile, project: Project): ContextName? =
      findWebSymbolsContext(kind, location, project)

    @JvmStatic
    fun get(kind: ContextKind, location: PsiElement): ContextName? =
      findWebSymbolsContext(kind, location)

    @JvmStatic
    fun empty(): WebSymbolsContext =
      WebSymbolsContextImpl.empty

    @JvmStatic
    fun create(map: Map<ContextKind, ContextName>): WebSymbolsContext =
      WebSymbolsContextImpl(map)

  }
}