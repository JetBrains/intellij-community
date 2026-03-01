// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.impl.PolyContextImpl
import com.intellij.polySymbols.context.impl.PolyContextProviderExtensionCollector
import com.intellij.polySymbols.context.impl.findPolyContext
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.TestOnly

interface PolyContext {

  operator fun get(kind: PolyContextKind): PolyContextName?

  @Suppress("MayBeConstant")
  companion object {

    @TestOnly
    @JvmField
    val POLY_SYMBOLS_CONTEXT_EP: KeyedExtensionCollector<PolyContextProvider, String> =
      PolyContextProviderExtensionCollector(ExtensionPointName("com.intellij.polySymbols.context"))

    @JvmField
    val VALUE_NONE: String = "none"

    @JvmField
    val POLY_SYMBOLS_CONTEXT_FILE: String = ".ws-context"

    @JvmField
    val PKG_MANAGER_NODE_PACKAGES: String = "node-packages"

    @JvmField
    val PKG_MANAGER_RUBY_GEMS: String = "ruby-gems"

    @JvmField
    val PKG_MANAGER_SYMFONY_BUNDLES: String = "symfony-bundles"

    @JvmStatic
    @RequiresReadLock
    fun get(kind: PolyContextKind, location: VirtualFile, project: Project): PolyContextName? =
      findPolyContext(kind, location, project)

    @JvmStatic
    @RequiresReadLock
    fun get(kind: PolyContextKind, location: PsiElement): PolyContextName? =
      findPolyContext(kind, location)

    @JvmStatic
    fun empty(): PolyContext =
      PolyContextImpl.empty

    @JvmStatic
    fun create(map: Map<PolyContextKind, PolyContextName>): PolyContext =
      PolyContextImpl(map)

  }
}