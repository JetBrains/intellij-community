// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.utils.PolySymbolScopeWithCache
import com.intellij.psi.PsiFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class PolySymbolScopeCachedDslTest {
  private val myFixture by sharedCodeInsightFixture(projectFixture)
  private val project get() = myFixture.project

  @Test
  fun testProjectScopeBuildsSuccessfully() {
    val scope = myScopeForProject(project)
    Assertions.assertTrue(scope is PolySymbolScopeWithCache<*, *>)
  }

  @Test
  fun testPsiScopeBuildsSuccessfully() {
    val file = myFixture.configureByText("a.txt", "hello")
    val scope = myScopeForFile(file)
    Assertions.assertTrue(scope is PolySymbolScopeWithCache<*, *>)
  }

  @Test
  fun testTwoProjectScopesFromSameHelperAreEqual() {
    val a = myScopeForProject(project)
    val b = myScopeForProject(project)
    Assertions.assertEquals(a, b)
    Assertions.assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun testTwoProjectScopesFromDifferentCallSitesAreNotEqual() {
    val a = myScopeForProject(project)
    val b = anotherScopeForProject(project)
    Assertions.assertNotEquals(a, b)
  }

  @Test
  fun testTwoPsiScopesWithDifferentKeysAreNotEqual() {
    val file = myFixture.configureByText("a.txt", "hello")
    val a = myScopeForFile(file, tagName = "foo")
    val b = myScopeForFile(file, tagName = "bar")
    Assertions.assertNotEquals(a, b)
  }

  @Test
  fun testTwoPsiScopesWithSameKeyAreEqual() {
    val file = myFixture.configureByText("a.txt", "hello")
    val a = myScopeForFile(file, tagName = "foo")
    val b = myScopeForFile(file, tagName = "foo")
    Assertions.assertEquals(a, b)
    Assertions.assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun testProjectScopePointerRoundTrip() {
    val scope = myScopeForProject(project) as PolySymbolScopeWithCache<*, *>
    val pointer = scope.createPointer()
    val restored = pointer.dereference()
    Assertions.assertNotNull(restored)
    Assertions.assertEquals(scope, restored)
  }

  @Test
  fun testPsiScopePointerRoundTrip() {
    val file = myFixture.configureByText("a.txt", "hello")
    val scope = myScopeForFile(file) as PolySymbolScopeWithCache<*, *>
    val restored = runReadActionBlocking { scope.createPointer().dereference() }
    Assertions.assertNotNull(restored)
    Assertions.assertEquals(scope, restored)
  }

  @Test
  fun testInitializeBodyIsRequired() {
    val e = Assertions.assertThrows(IllegalStateException::class.java) {
      polySymbolScopeCached(project) {
        provides(TEST_KIND)
      }
    }
    Assertions.assertTrue(e.message!!.contains("initialize { } was not called"))
  }

  @Test
  fun testGenericOverloadRequiresPointer() {
    val holder = SimpleHolder()
    val e = Assertions.assertThrows(IllegalStateException::class.java) {
      polySymbolScopeCached(project, holder) {
        provides(TEST_KIND)
        initialize { cacheDependencies(ModificationTracker.NEVER_CHANGED) }
      }
    }
    Assertions.assertTrue(e.message!!.contains("pointer { } is required"))
  }

  private class SimpleHolder : UserDataHolderBase()

  companion object {
    private val projectFixture = sharedCodeInsightProjectFixture()

    private val TEST_KIND: PolySymbolKind = PolySymbolKind.Companion["test", "testKind"]

    private fun myScopeForProject(project: Project): PolySymbolScope =
      polySymbolScopeCached(project) {
        provides(TEST_KIND)
        initialize {
          cacheDependencies(ModificationTracker.NEVER_CHANGED)
        }
      }

    private fun anotherScopeForProject(project: Project): PolySymbolScope =
      polySymbolScopeCached(project) {
        provides(TEST_KIND)
        initialize {
          cacheDependencies(ModificationTracker.NEVER_CHANGED)
        }
      }

    private fun myScopeForFile(file: PsiFile, tagName: String = "default"): PolySymbolScope =
      polySymbolScopeCached(file, tagName) {
        provides(TEST_KIND)
        initialize {
          cacheDependencies(ModificationTracker.NEVER_CHANGED)
        }
      }
  }
}