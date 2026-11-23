// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanSearchScopeTest : BasePlatformTestCase() {
  fun `test a request with no scope searches the project`() {
    val file = myFixture.configureByText("a.txt", "a")

    val request = runReadActionBlocking { ModPlanRequest(project, listOf(SymbolPointer.of(file)), ModPlanOptionValues.EMPTY) }

    assertEquals(GlobalSearchScope.projectScope(project), runReadActionBlocking { request.searchScope().get() })
  }

  fun `test a global scope is kept as it is`() {
    val file = myFixture.configureByText("a.txt", "a")
    val fileScope = GlobalSearchScope.fileScope(file)

    val requestScope = runReadActionBlocking { ModPlanRequest.searchScopeSupplier(fileScope) }

    assertSame(fileScope, runReadActionBlocking { requestScope.get() })
  }

  fun `test a local scope is made again after a change of its file`() {
    val file = myFixture.configureByText("a.txt", "text")
    val requestScope = runReadActionBlocking { ModPlanRequest.searchScopeSupplier(LocalSearchScope(arrayOf(file), "Selection", true)) }

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.insertString(0, "more ")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val localScope = runReadActionBlocking { requestScope.get() } as LocalSearchScope
    assertEquals("Selection", localScope.displayName)
    assertTrue(localScope.isIgnoreInjectedPsi)
    assertEquals(file, localScope.scope.single())
  }

  fun `test the request after an answer keeps the scope`() {
    val file = myFixture.configureByText("a.txt", "a")
    val requestScope = runReadActionBlocking { ModPlanRequest.searchScopeSupplier(GlobalSearchScope.fileScope(file)) }
    val request = runReadActionBlocking { ModPlanRequest(project, listOf(SymbolPointer.of(file)), ModPlanOptionValues.EMPTY, requestScope) }

    val answeredRequest = ModPlanChain.withOptionValues(request, ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true))

    assertSame(requestScope, answeredRequest.searchScope())
  }
}
