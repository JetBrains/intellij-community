// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.psi.PsiElement
import javax.swing.Icon

abstract class PolySymbolFramework {

  lateinit var id: String
    internal set

  // required to accommodate com.intellij.javascript.web.WebFramework.UnregisteredWebFramework
  protected fun setIdFromAnotherModule(id: String) {
    this.id = id
  }

  abstract val displayName: String

  open val icon: Icon?
    get() = null

  open fun getNames(qualifiedName: PolySymbolQualifiedName, target: PolySymbolNamesProvider.Target): List<String> = emptyList()

  fun isInContext(location: PsiElement): Boolean = PolyContext.get(KIND_FRAMEWORK, location) == id

  fun isInContext(location: VirtualFile, project: Project): Boolean = PolyContext.get(KIND_FRAMEWORK, location, project) == id

  companion object {

    private val WEB_FRAMEWORK_EP = object : KeyedExtensionCollector<PolySymbolFramework, String>("com.intellij.polySymbols.framework") {
      val all get() = extensions.asSequence().map { it.instance }
    }

    @JvmStatic
    fun get(id: String): PolySymbolFramework = WEB_FRAMEWORK_EP.findSingle(id) ?: UnregisteredWebFramework(id)

    @JvmStatic
    fun inLocation(location: VirtualFile, project: Project): PolySymbolFramework? = PolyContext.get(KIND_FRAMEWORK, location, project)?.let { get(it) }

    @JvmStatic
    fun inLocation(location: PsiElement): PolySymbolFramework? = PolyContext.get(KIND_FRAMEWORK, location)?.let { get(it) }

    @JvmStatic
    val all: List<PolySymbolFramework>
      get() = WEB_FRAMEWORK_EP.all.toList()

    @JvmStatic
    internal val allAsSequence: Sequence<PolySymbolFramework>
      get() = WEB_FRAMEWORK_EP.all

  }

  private class UnregisteredWebFramework(id: String) : PolySymbolFramework() {
    init {
      this.id = id
    }

    override val displayName: String
      get() = id

  }

}