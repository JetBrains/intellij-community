// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.folding

import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import kotlin.test.assertNotNull as kAssertNotNull

class ComposeResourcesFoldingTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test string resources are folded`() = doFoldingTest { descriptors ->
    val folding = descriptors.findByResourceRef("Res.string.test")
    assertNotNull("Folding descriptor for Res.string.test should exist", folding)
    assertEquals("\"test\"", folding?.placeholderText)
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test plural resources are folded`() = doFoldingTest { descriptors ->
    val folding = descriptors.findByResourceRef("Res.plurals.test")
    assertNotNull("Folding descriptor for Res.plurals.test should exist", folding)
    assertEquals("\"%d zero\"", folding?.placeholderText)
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test non-string resources are not folded`() = doFoldingTest { descriptors ->
    assertNull("Folding descriptor for Res.drawable.test should not exist", descriptors.findByResourceRef("Res.drawable.test"))
    assertNull("Folding descriptor for Res.font.test should not exist", descriptors.findByResourceRef("Res.font.test"))
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test folding is collapsed by default`() = doFoldingTest { descriptors ->
    assertTrue("There should be at least one folding descriptor", descriptors.isNotEmpty())
    descriptors.filterNotNull().forEach { descriptor ->
      assertEquals("Folding should be collapsed by default", true, descriptor.isCollapsedByDefault)
    }
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quick mode returns no folding`() = doFoldingTest(quick = true) { descriptors ->
    assertTrue("Quick mode should return no folding descriptors", descriptors.isEmpty())
  }

  private fun doFoldingTest(quick: Boolean = false, assertions: (Array<out FoldingDescriptor?>) -> Unit) {
    val files = importProjectFromTestData()

    timeoutRunBlocking(context = Dispatchers.EDT) {

      codeInsightTestFixture.openFileInEditor(files.first { it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt") })

      val foldingBuilder = ComposeResourcesFoldingBuilder()
      val psiFile = codeInsightTestFixture.file
      val document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile)
      kAssertNotNull(document, "Document should not be null")
      val descriptors = foldingBuilder.buildFoldRegions(psiFile, document, quick)

      assertions(descriptors)
    }
  }

  private fun Array<out FoldingDescriptor?>.findByResourceRef(ref: String): FoldingDescriptor? =
    find { it?.element?.psi?.text?.contains(ref) == true }
}
