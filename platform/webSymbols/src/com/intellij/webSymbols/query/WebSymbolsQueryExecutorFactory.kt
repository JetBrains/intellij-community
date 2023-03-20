// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbolsScope
import org.jetbrains.annotations.TestOnly

interface WebSymbolsQueryExecutorFactory : Disposable {

  fun create(location: PsiElement?, allowResolve: Boolean = true): WebSymbolsQueryExecutor

  @TestOnly
  fun addScope(scope: WebSymbolsScope, contextDirectory: VirtualFile?, disposable: Disposable)

  companion object {

    @JvmStatic
    fun getInstance(project: Project): WebSymbolsQueryExecutorFactory = project.service()

    fun create(location: PsiElement, allowResolve: Boolean = true): WebSymbolsQueryExecutor =
      location.project.service<WebSymbolsQueryExecutorFactory>().create(location, allowResolve)

  }

}