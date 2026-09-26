// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestApplication
class CodeInsightContextAwareSearchScopesTest {

  private val file1: VirtualFile = LightVirtualFile("file1.txt")
  private val file2: VirtualFile = LightVirtualFile("file2.txt")

  @Nested
  inner class ExtensionApi {
    @Test
    fun `non-aware scope returns NoContextInformation`() {
      val scope = FakeScope(setOf(file1))
      assertIs<NoContextInformation>(scope.codeInsightContextInfo)
    }

    @Test
    fun `aware scope reporting NoContextInformation is plumbed through`() {
      val scope = FakeAwareScope(setOf(file1), NoContextInformation())
      assertIs<NoContextInformation>(scope.codeInsightContextInfo)
    }

    @Test
    fun `contains(file, context) on non-aware scope falls back to contains(file)`() {
      val scope = FakeScope(setOf(file1))
      assertTrue(scope.contains(file1, ContextA))
      assertTrue(scope.contains(file1, ContextB))
      assertFalse(scope.contains(file2, ContextA))
      assertFalse(scope.contains(file2, ContextB))
    }

    @Test
    fun `contains(file, context) on aware scope with NoContextInformation falls back to contains(file)`() {
      val scope = FakeAwareScope(setOf(file1), NoContextInformation())
      assertTrue(scope.contains(file1, ContextA))
      assertTrue(scope.contains(file1, ContextB))
      assertFalse(scope.contains(file2, ContextA))
      assertFalse(scope.contains(file2, ContextB))
    }

    @Test
    fun `contains(file, context) on actual aware scope delegates to info`() {
      val info = FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA))))
      val scope = FakeAwareScope(setOf(file1), info)
      assertTrue(scope.contains(file1, ContextA))
      assertFalse(scope.contains(file1, ContextB))
      assertFalse(scope.contains(file2, ContextA))
      assertFalse(scope.contains(file2, ContextB))
    }

    @Test
    fun `getFileContextInfo on non-aware scope returns DoesNotContain when file is missing`() {
      val scope = FakeScope(setOf(file1))
      assertIs<DoesNotContainFileInfo>(scope.getFileContextInfo(file2))
    }

    @Test
    fun `getFileContextInfo on non-aware scope returns NoContextFileInfo when file is present`() {
      val scope = FakeScope(setOf(file1))
      assertIs<NoContextFileInfo>(scope.getFileContextInfo(file1))
    }

    @Test
    fun `getFileContextInfo on actual aware scope delegates to info`() {
      val info = FakeActualInfo(mapOf(
        file1 to ActualContextFileInfo(listOf(ContextA)),
        file2 to NoContextFileInfo(),
      ))
      val scope = FakeAwareScope(setOf(file1, file2), info)

      val actualFor1 = scope.getFileContextInfo(file1)
      assertIs<ActualContextFileInfo>(actualFor1)
      assertEquals(listOf(ContextA), actualFor1.contexts.toList())

      assertIs<NoContextFileInfo>(scope.getFileContextInfo(file2))
      assertIs<DoesNotContainFileInfo>(scope.getFileContextInfo(LightVirtualFile("other.txt")))

      assertTrue(scope.contains(file1, ContextA))
      assertFalse(scope.contains(file1, ContextB))
      assertFalse(scope.contains(file2, ContextA))
      assertFalse(scope.contains(file2, ContextB))
      assertTrue(scope.contains(file1))
      assertTrue(scope.contains(file2))
    }

    @Test
    fun `getCorrespondingContexts returns empty for DoesNotContain`() {
      val scope = FakeScope(setOf(file1))
      assertTrue(scope.getCorrespondingContexts(file2).isEmpty())
    }

    @Test
    fun `getCorrespondingContexts returns anyContext for NoContextFileInfo`() {
      val scope = FakeScope(setOf(file1))
      assertEquals(listOf(anyContext()), scope.getCorrespondingContexts(file1).toList())
    }

    @Test
    fun `getCorrespondingContexts returns the actual contexts`() {
      val info = FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA, ContextB))))
      val scope = FakeAwareScope(setOf(file1), info)
      assertEquals(setOf(ContextA, ContextB), scope.getCorrespondingContexts(file1).toSet())
    }
  }

  @Nested
  inner class Union {
    @Test
    fun `union of two non-aware scopes returns NoContextInformation`() {
      val s1 = FakeScope(setOf(file1))
      val s2 = FakeScope(setOf(file2))
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      assertIs<NoContextInformation>(union.codeInsightContextInfo)
    }

    @Test
    fun `union of aware scopes that all report NoContextInformation short-circuits to NoContextInformation`() {
      // Symmetric with createIntersectionCodeInsightContextInfo: a scope that's aware but contributes
      // no ActualCodeInsightContextInfo carries no extra signal, so the union collapses to
      // NoContextInformation rather than an indirect ActualCodeInsightContextInfo wrapper.
      val s1 = FakeAwareScope(setOf(file1), NoContextInformation())
      val s2 = FakeAwareScope(setOf(file2), NoContextInformation())
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      assertIs<NoContextInformation>(union.codeInsightContextInfo)
    }

    @Test
    fun `union of aware-but-NoContextInformation with non-aware also collapses to NoContextInformation`() {
      val s1 = FakeAwareScope(setOf(file1), NoContextInformation())
      val s2 = FakeScope(setOf(file2))
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      assertIs<NoContextInformation>(union.codeInsightContextInfo)
    }

    @Test
    fun `union of aware and non-aware returns ActualCodeInsightContextInfo`() {
      val aware = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val nonAware = FakeScope(setOf(file2))
      val union = GlobalSearchScope.union(arrayOf(aware, nonAware))
      assertIs<ActualCodeInsightContextInfo>(union.codeInsightContextInfo)
    }

    @Test
    fun `union contains - any scope containing the file in the given context is enough`() {
      val s1 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val s2 = FakeScope(emptySet())
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      assertTrue(union.contains(file1, ContextA))
      assertFalse(union.contains(file1, ContextB))
    }

    @Test
    fun `union contains - non-aware scope containing the file satisfies any context`() {
      val nonAware = FakeScope(setOf(file1))
      val aware = FakeAwareScope(setOf(file2), FakeActualInfo(emptyMap()))
      val union = GlobalSearchScope.union(arrayOf(nonAware, aware))
      assertTrue(union.contains(file1, ContextA))
      assertTrue(union.contains(file1, ContextB))
    }

    @Test
    fun `union getFileInfo - no scope contains returns DoesNotContain`() {
      val s1 = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val s2 = FakeScope(emptySet())
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      val info = union.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `union getFileInfo - only non-aware scope contains - returns NoContextFileInfo`() {
      val s1 = FakeScope(setOf(file1))
      val s2 = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      val info = union.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<NoContextFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `union getFileInfo - merges contexts from multiple aware scopes`() {
      val s1 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA, ContextB)))),
      )
      val s2 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextB, ContextC)))),
      )
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      val result = (union.codeInsightContextInfo as ActualCodeInsightContextInfo).getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextA, ContextB, ContextC), result.contexts.toSet())
    }

    @Test
    fun `union getFileInfo - aware scope reporting NoContextFileInfo is dropped from the merge`() {
      val s1 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val s2 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to NoContextFileInfo())),
      )
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      val result = (union.codeInsightContextInfo as ActualCodeInsightContextInfo).getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextA), result.contexts.toSet())
    }

    @Test
    fun `union getFileInfo IJPL-339 - aware contexts win over non-aware containing`() {
      // Documents the current behavior referenced by the IJPL-339 TODO in CodeInsightContextInfoUnion.kt:
      // when only the aware scope contributes contexts, the non-aware scope's "I also contain this file"
      // signal is silently lost.
      val aware = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val nonAware = FakeScope(setOf(file1))
      val union = GlobalSearchScope.union(arrayOf(aware, nonAware))
      val result = (union.codeInsightContextInfo as ActualCodeInsightContextInfo).getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextA), result.contexts.toSet())
    }

    @Test
    fun `union getFileInfo - aware scopes that do not contain the file are ignored`() {
      val s1 = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val s2 = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val union = GlobalSearchScope.union(arrayOf(s1, s2))
      val result = (union.codeInsightContextInfo as ActualCodeInsightContextInfo).getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextA), result.contexts.toSet())
    }
  }

  @Nested
  inner class Intersection {
    @Test
    fun `intersection of two non-aware scopes returns NoContextInformation`() {
      val s1 = FakeScope(setOf(file1, file2))
      val s2 = FakeScope(setOf(file1))
      val intersection = s1.intersectWith(s2)
      assertIs<NoContextInformation>(intersection.codeInsightContextInfo)
    }

    @Test
    fun `intersection of aware and non-aware returns ActualCodeInsightContextInfo`() {
      val aware = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val nonAware = FakeScope(setOf(file1))
      val intersection = aware.intersectWith(nonAware)
      assertIs<ActualCodeInsightContextInfo>(intersection.codeInsightContextInfo)
    }

    @Test
    fun `intersection of aware scope that reports NoContextInformation is treated as non-aware`() {
      // createIntersectionCodeInsightContextInfo only keeps the side that actually exposes
      // ActualCodeInsightContextInfo. A scope returning NoContextInformation falls into the
      // Intersection1 "gating scope" branch, contributing only membership.
      val awareNoInfo = FakeAwareScope(setOf(file1), NoContextInformation())
      val actualAware = FakeAwareScope(
        setOf(file1, file2),
        FakeActualInfo(mapOf(
          file1 to ActualContextFileInfo(listOf(ContextA)),
          file2 to ActualContextFileInfo(listOf(ContextA)),
        )),
      )
      val intersection = awareNoInfo.intersectWith(actualAware)
      val info = intersection.codeInsightContextInfo
      assertIs<ActualCodeInsightContextInfo>(info)
      assertTrue(info.contains(file1, ContextA))
      assertFalse(info.contains(file1, ContextB)) // file1 has only ContextA in actualAware
      assertFalse(info.contains(file2, ContextA)) // file2 not in the gating awareNoInfo scope
      assertFalse(info.contains(file2, ContextB))
    }

    @Test
    fun `intersection contains requires both scopes to contain the file in the context`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA, ContextB)))))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertTrue(info.contains(file1, ContextA))
      assertFalse(info.contains(file1, ContextB))
    }

    @Test
    fun `Intersection1 contains requires the gating scope to contain the file`() {
      val aware = FakeAwareScope(
        setOf(file1),
        FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))),
      )
      val nonAware = FakeScope(emptySet())
      val intersection = aware.intersectWith(nonAware)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertFalse(info.contains(file1, ContextA))
      assertFalse(info.contains(file1, ContextB))
    }

    @Test
    fun `intersection getFileInfo - DoesNotContain from info1 dominates`() {
      val a = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `intersection getFileInfo - DoesNotContain from info2 dominates`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val b = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `intersection getFileInfo - NoContextFileInfo from info1 dominates over Actual`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to NoContextFileInfo())))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<NoContextFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `intersection getFileInfo - NoContextFileInfo from info2 dominates over Actual`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to NoContextFileInfo())))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<NoContextFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `intersection getFileInfo - intersects contexts when both Actual`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA, ContextB)))))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextB, ContextC)))))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      val result = info.getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextB), result.contexts.toSet())
    }

    @Test
    fun `intersection getFileInfo - empty context intersection returns DoesNotContain`() {
      val a = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val b = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextB)))))
      val intersection = a.intersectWith(b)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `Intersection1 getFileInfo - gating scope does not contain returns DoesNotContain`() {
      val aware = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val nonAware = FakeScope(emptySet())
      val intersection = aware.intersectWith(nonAware)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `Intersection1 getFileInfo - gating scope contains - returns info1 Actual result`() {
      val aware = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to ActualContextFileInfo(listOf(ContextA)))))
      val nonAware = FakeScope(setOf(file1))
      val intersection = aware.intersectWith(nonAware)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      val result = info.getFileInfo(file1)
      assertIs<ActualContextFileInfo>(result)
      assertEquals(setOf(ContextA), result.contexts.toSet())
    }

    @Test
    fun `Intersection1 getFileInfo - gating scope contains - returns info1 NoContextFileInfo result`() {
      val aware = FakeAwareScope(setOf(file1), FakeActualInfo(mapOf(file1 to NoContextFileInfo())))
      val nonAware = FakeScope(setOf(file1))
      val intersection = aware.intersectWith(nonAware)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<NoContextFileInfo>(info.getFileInfo(file1))
    }

    @Test
    fun `Intersection1 getFileInfo - gating scope contains - returns info1 DoesNotContain result`() {
      val aware = FakeAwareScope(emptySet(), FakeActualInfo(emptyMap()))
      val nonAware = FakeScope(setOf(file1))
      val intersection = aware.intersectWith(nonAware)
      val info = intersection.codeInsightContextInfo as ActualCodeInsightContextInfo
      assertIs<DoesNotContainFileInfo>(info.getFileInfo(file1))
    }
  }
}

// ---------------- test fixtures ----------------

private object ContextA : CodeInsightContext { override fun toString(): String = "A" }
private object ContextB : CodeInsightContext { override fun toString(): String = "B" }
private object ContextC : CodeInsightContext { override fun toString(): String = "C" }

private class FakeScope(private val files: Set<VirtualFile>) : GlobalSearchScope(null) {
  override fun contains(file: VirtualFile): Boolean = file in files
  override fun isSearchInModuleContent(aModule: Module): Boolean = false
  override fun isSearchInLibraries(): Boolean = false
}

private class FakeAwareScope(
  private val files: Set<VirtualFile>,
  override val codeInsightContextInfo: CodeInsightContextInfo,
) : GlobalSearchScope(null), CodeInsightContextAwareSearchScope {
  override fun contains(file: VirtualFile): Boolean = file in files
  override fun isSearchInModuleContent(aModule: Module): Boolean = false
  override fun isSearchInLibraries(): Boolean = false
}

private class FakeActualInfo(
  private val perFile: Map<VirtualFile, CodeInsightContextFileInfo>,
) : ActualCodeInsightContextInfo {
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    val info = perFile[file] as? ActualContextFileInfo ?: return false
    return context in info.contexts
  }

  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo =
    perFile[file] ?: DoesNotContainFileInfo()
}
