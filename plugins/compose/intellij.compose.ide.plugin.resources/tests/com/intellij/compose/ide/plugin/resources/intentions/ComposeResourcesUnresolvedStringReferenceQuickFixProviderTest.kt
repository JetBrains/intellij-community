// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.CreateStringResourceQuickFix
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.findTestFiles
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.invokeAndAssertQuickFixResult
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.concurrent.ForkJoinPool

internal class ComposeResourcesUnresolvedStringReferenceQuickFixProviderTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.new_string_resource", shouldRegister = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.new_string_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string reference fully qualified`() =
    doQuickFixRegistrationAndExecution("val x = composeresources.composeapp.generated.resources.Res.string.new_string_resource",
                                       shouldRegister = true)


  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string reference with spaces`() =
    doQuickFixRegistrationAndExecution("val x = Res . string  .   new_string_resource", shouldRegister = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string-array reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.new_array_resource", shouldRegister = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved string-array reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.new_array_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved plurals reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.new_plurals_resource", shouldRegister = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is registered for unresolved plurals reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.new_plurals_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for drawable reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.drawable.nonexistent", shouldRegister = false)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for font reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.font.nonexistent", shouldRegister = false)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for already existing string resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.test", shouldRegister = false)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for already existing string-array resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.test", shouldRegister = false)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for already existing plurals resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.test", shouldRegister = false)

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix is not registered for drawable`() =
    doQuickFixRegistrationAndExecution("val x = Res.drawable.test", shouldRegister = false)

  private fun doQuickFixRegistrationAndExecution(
    codeLine: String,
    shouldRegister: Boolean,
    deleteStringsXmlBeforeRegistration: Boolean = false,
  ) {
    invokeAndWaitIfNeeded(ModalityState.nonModal()) {
      val files = importProjectFromTestData()
      val (sourceKtFile, stringsXmlFile) = files.findTestFiles(myProject, sourceSetName)
      val composeResourcesDir = stringsXmlFile.parent.parent

      if (deleteStringsXmlBeforeRegistration) {
        runWriteAction {
          stringsXmlFile.delete(this)
        }
      }

      codeInsightTestFixture.openFileInEditor(sourceKtFile)
      val document = codeInsightTestFixture.editor.document
      runWriteAction { document.insertString(document.textLength, "\n$codeLine") }
      codeInsightTestFixture.editor.caretModel.moveToOffset(document.textLength)

      codeInsightTestFixture.doHighlighting()

      val quickFixes = collectQuickFixesViaProvider()

      if (shouldRegister) {
        assertTrue("Expected quickfix to be registered for '$codeLine', but none found", quickFixes.isNotEmpty())
      }
      else {
        assertTrue("Expected no quickfix for '$codeLine', but found: ${quickFixes.joinToString { it.text }}", quickFixes.isEmpty())
        return@invokeAndWaitIfNeeded
      }

      assertTrue("Only one quickfix should be registered for '$codeLine'", quickFixes.size == 1)
      val quickFix = quickFixes.first()

      invokeAndAssertQuickFixResult(
        quickFix = quickFix,
        composeResourcesDirVirtualFile = composeResourcesDir,
        project = myProject,
        codeLine = codeLine,
        expectAdded = true,
      )
    }
  }

  private fun collectQuickFixesViaProvider(): List<CreateStringResourceQuickFix> {
    val fixes = mutableListOf<IntentionAction>()
    val registrar = object : QuickFixActionRegistrar {
      override fun register(action: IntentionAction) {
        fixes.add(action)
      }

      override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
        fixes.add(action)
      }
    }

    val future = ReadAction.nonBlocking<Unit> {
      val offset = codeInsightTestFixture.editor.caretModel.offset
      val psiFile = codeInsightTestFixture.file
      val reference = findKtSimpleNameReference(psiFile, offset - 1) ?: return@nonBlocking
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, registrar)
    }.submit(ForkJoinPool.commonPool())

    PlatformTestUtil.waitForPromise(future, 30_000)
    return fixes.filterIsInstance<CreateStringResourceQuickFix>()
  }

}

private fun findKtSimpleNameReference(psiFile: PsiFile, offset: Int): KtSimpleNameReference? {
  val element = psiFile.findElementAt(offset) ?: return null
  val nameRef = element.parent as? KtNameReferenceExpression ?: return null
  return nameRef.references.filterIsInstance<KtSimpleNameReference>().firstOrNull()
}