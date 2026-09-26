// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.compose.ide.plugin.resources.ComposeResourcesAllSourceSets
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.CreateStringResourceQuickFix
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.findStringsXmlFile
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.invokeAndAssertQuickFixResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ComposeResourcesAllSourceSets
internal class ComposeResourcesUnresolvedStringReferenceQuickFixProviderTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test quickfix is registered for unresolved string reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.new_string_resource", shouldRegister = true)

  @Test
  fun `test quickfix is registered for unresolved string reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.new_string_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @Test
  fun `test quickfix is registered for unresolved string reference fully qualified`() =
    doQuickFixRegistrationAndExecution("val x = composeresources.composeapp.generated.resources.Res.string.new_string_resource",
                                       shouldRegister = true)


  @Test
  fun `test quickfix is registered for unresolved string reference with spaces`() =
    doQuickFixRegistrationAndExecution("val x = Res . string  .   new_string_resource", shouldRegister = true)

  @Test
  fun `test quickfix is registered for unresolved string-array reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.new_array_resource", shouldRegister = true)

  @Test
  fun `test quickfix is registered for unresolved string-array reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.new_array_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @Test
  fun `test quickfix is registered for unresolved plurals reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.new_plurals_resource", shouldRegister = true)

  @Test
  fun `test quickfix is registered for unresolved plurals reference and strings_xml is missing`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.new_plurals_resource",
                                       shouldRegister = true,
                                       deleteStringsXmlBeforeRegistration = true)

  @Test
  fun `test quickfix is not registered for drawable reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.drawable.nonexistent", shouldRegister = false)

  @Test
  fun `test quickfix is not registered for font reference`() =
    doQuickFixRegistrationAndExecution("val x = Res.font.nonexistent", shouldRegister = false)

  @Test
  fun `test quickfix is not registered for already existing string resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.string.test", shouldRegister = false)

  @Test
  fun `test quickfix is not registered for already existing string-array resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.array.test", shouldRegister = false)

  @Test
  fun `test quickfix is not registered for already existing plurals resource`() =
    doQuickFixRegistrationAndExecution("val x = Res.plurals.test", shouldRegister = false)

  @Test
  fun `test quickfix is not registered for drawable`() =
    doQuickFixRegistrationAndExecution("val x = Res.drawable.test", shouldRegister = false)

  private fun doQuickFixRegistrationAndExecution(
    codeLine: String,
    shouldRegister: Boolean,
    deleteStringsXmlBeforeRegistration: Boolean = false,
  ) = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      try {
        val stringsXmlFile = findStringsXmlFile(project, sourceSetName)
        val composeResourcesDir = stringsXmlFile.parent.parent
        snapshotProjectFile(projectRelativePath(stringsXmlFile))

        if (deleteStringsXmlBeforeRegistration) {
          edtWriteAction { stringsXmlFile.delete(this) }
        }

        codeInsightFixture.configureFromExistingVirtualFile(
          canonicalProjectFile("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
        )
        val document = codeInsightFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) {
          document.insertString(document.textLength, "\n$codeLine")
        }
        codeInsightFixture.editor.caretModel.moveToOffset(document.textLength)

        codeInsightFixture.doHighlighting()

        val quickFixes = collectQuickFixesViaProvider()

        if (!shouldRegister) {
          assertTrue(quickFixes.isEmpty(), "Expected no quickfix for '$codeLine', but found: ${quickFixes.joinToString { it.text }}")
          return@timeoutRunBlocking
        }

        assertTrue(quickFixes.isNotEmpty(), "Expected quickfix to be registered for '$codeLine', but none found")
        assertTrue(quickFixes.size == 1, "Only one quickfix should be registered for '$codeLine'")

        invokeAndAssertQuickFixResult(
          quickFix = quickFixes.first(),
          composeResourcesDirVirtualFile = composeResourcesDir,
          project = project,
          codeLine = codeLine,
          expectAdded = true,
        )
      }
      finally {
        revertUnsavedDocuments()
      }
    }
  }

  private suspend fun collectQuickFixesViaProvider(): List<CreateStringResourceQuickFix> {
    val fixes = mutableListOf<IntentionAction>()
    val registrar = object : QuickFixActionRegistrar {
      override fun register(action: IntentionAction) {
        fixes.add(action)
      }

      override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
        fixes.add(action)
      }
    }

    val offset = codeInsightFixture.editor.caretModel.offset
    val psiFile = codeInsightFixture.file
    readAction {
      val reference = findKtSimpleNameReference(psiFile, offset - 1) ?: return@readAction
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, registrar)
    }
    return fixes.filterIsInstance<CreateStringResourceQuickFix>()
  }
}

private fun findKtSimpleNameReference(psiFile: PsiFile, offset: Int): KtSimpleNameReference? {
  val element = psiFile.findElementAt(offset) ?: return null
  val nameRef = element.parent as? KtNameReferenceExpression ?: return null
  return nameRef.references.filterIsInstance<KtSimpleNameReference>().firstOrNull()
}
