// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.utils.PolySymbolScopeWithCache
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

class PolySymbolScopeCachedDslTest : BasePlatformTestCase() {

  fun testProjectScopeBuildsSuccessfully() {
    val scope = myScopeForProject(project)
    Assert.assertTrue(scope is PolySymbolScopeWithCache<*, *>)
  }

  fun testPsiScopeBuildsSuccessfully() {
    val file = myFixture.configureByText("a.txt", "hello")
    val scope = myScopeForFile(file)
    Assert.assertTrue(scope is PolySymbolScopeWithCache<*, *>)
  }

  fun testTwoProjectScopesFromSameHelperAreEqual() {
    val a = myScopeForProject(project)
    val b = myScopeForProject(project)
    Assert.assertEquals(a, b)
    Assert.assertEquals(a.hashCode(), b.hashCode())
  }

  fun testTwoProjectScopesFromDifferentCallSitesAreNotEqual() {
    val a = myScopeForProject(project)
    val b = anotherScopeForProject(project)
    Assert.assertNotEquals(a, b)
  }

  fun testTwoPsiScopesWithDifferentKeysAreNotEqual() {
    val file = myFixture.configureByText("a.txt", "hello")
    val a = myScopeForFile(file, tagName = "foo")
    val b = myScopeForFile(file, tagName = "bar")
    Assert.assertNotEquals(a, b)
  }

  fun testTwoPsiScopesWithSameKeyAreEqual() {
    val file = myFixture.configureByText("a.txt", "hello")
    val a = myScopeForFile(file, tagName = "foo")
    val b = myScopeForFile(file, tagName = "foo")
    Assert.assertEquals(a, b)
    Assert.assertEquals(a.hashCode(), b.hashCode())
  }

  fun testProjectScopePointerRoundTrip() {
    val scope = myScopeForProject(project) as PolySymbolScopeWithCache<*, *>
    val pointer = scope.createPointer()
    val restored = pointer.dereference()
    Assert.assertNotNull(restored)
    Assert.assertEquals(scope, restored)
  }

  fun testPsiScopePointerRoundTrip() {
    val file = myFixture.configureByText("a.txt", "hello")
    val scope = myScopeForFile(file) as PolySymbolScopeWithCache<*, *>
    val pointer = scope.createPointer()
    val restored = pointer.dereference()
    Assert.assertNotNull(restored)
    Assert.assertEquals(scope, restored)
  }

  fun testInitializeBodyIsRequired() {
    val e = Assert.assertThrows(IllegalStateException::class.java) {
      polySymbolScopeCached(project) {
        provides(TEST_KIND)
      }
    }
    Assert.assertTrue(e.message!!.contains("initialize { } was not called"))
  }

  fun testGenericOverloadRequiresPointer() {
    val holder = SimpleHolder()
    val e = Assert.assertThrows(IllegalStateException::class.java) {
      polySymbolScopeCached(project, holder) {
        provides(TEST_KIND)
        initialize { cacheDependencies(ModificationTracker.NEVER_CHANGED) }
      }
    }
    Assert.assertTrue(e.message!!.contains("pointer { } is required"))
  }

  private class SimpleHolder : UserDataHolderBase()

  companion object {
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