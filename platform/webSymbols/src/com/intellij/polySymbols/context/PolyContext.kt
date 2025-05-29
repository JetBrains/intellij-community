// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.ContextKind
import com.intellij.polySymbols.ContextName
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.context.impl.PolyContextImpl
import com.intellij.polySymbols.context.impl.PolyContextProviderExtensionCollector
import com.intellij.polySymbols.context.impl.findWebSymbolsContext
import org.jetbrains.annotations.TestOnly

interface PolyContext {

  val framework: FrameworkId?
    get() = this[KIND_FRAMEWORK]

  operator fun get(kind: ContextKind): ContextName?

  @Suppress("MayBeConstant")
  companion object {

    @TestOnly
    @JvmField
    val WEB_SYMBOLS_CONTEXT_EP: KeyedExtensionCollector<PolyContextProvider, String> =
      PolyContextProviderExtensionCollector(ExtensionPointName("com.intellij.webSymbols.context"))

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
    fun empty(): PolyContext =
      PolyContextImpl.empty

    @JvmStatic
    fun create(map: Map<ContextKind, ContextName>): PolyContext =
      PolyContextImpl(map)

  }
}