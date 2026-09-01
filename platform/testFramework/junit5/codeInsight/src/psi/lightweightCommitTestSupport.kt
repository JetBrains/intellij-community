// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions

/**
 * A single document edit together with the language-specific assertions for the PSI produced by
 * [PsiDocumentManager.commitDocument] in versioned environment.
 */
@TestOnly
class LightweightCommitScenario<F : PsiFile>(
  val name: String,
  val updatedText: String,
  val assertPsi: (F) -> Unit,
) {
  override fun toString(): String = name
}

/**
 * Runs [action] in an environment suitable for lightweight commit tests: background commit is disabled,
 * so the only commits that happen are the ones the test performs explicitly.
 */
@TestOnly
fun runVersionedTest(project: Project, action: suspend () -> Unit) {
  val disposable = Disposer.newDisposable("lightweight commit test")
  (PsiDocumentManagerBase.getInstance(project) as PsiDocumentManagerBase).disableBackgroundCommit(disposable)
  try {
    timeoutRunBlocking(context = Dispatchers.Default) {
      action()
    }
  }
  finally {
    Disposer.dispose(disposable)
  }
}

/**
 * Exposed due to `inline` of the other [assertLightweightCommitScenario]
 */
@TestOnly
suspend fun <F : PsiFile> assertLightweightCommitScenario(
  project: Project,
  document: Document,
  baseText: String,
  scenario: LightweightCommitScenario<F>,
  fileClass: Class<F>,
  assertCommonPsi: (F) -> Unit = {},
) {
  resetToBaseText(project, document, baseText)

  val lightweightText = withContext(Dispatchers.UiWithModelAccess) {
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText(scenario.updatedText)
    }
    PsiDocumentManager.getInstance(project).allowIsolatedCommits(document) {
      ThreadingAssertions.assertNoReadAccess()
      Assertions.assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)

      PsiDocumentManager.getInstance(project).commitDocument(document)

      val psiFile = currentPsiFile(project, document)
      Assertions.assertEquals(document.immutableCharSequence.toString(), psiFile.text)
      Assertions.assertTrue(fileClass.isInstance(psiFile),
                            "Expected a ${fileClass.name} PSI file, got ${psiFile::class.java.name}")
      val typedFile = fileClass.cast(psiFile)
      assertCommonPsi(typedFile)
      scenario.assertPsi(typedFile)

      psiFile.text
    }
  }

  Assertions.assertEquals(scenario.updatedText, lightweightText)
}

/**
 * A generic runner for a lightweight commit scenario.
 *
 * The sequence of execution is the following:
 * 1. [document] text is reset to [baseText] in write action
 * 2. [document] is committed -- this is a regular write-action commit
 * 3. [document] text is set to [LightweightCommitScenario.updatedText] of [scenario] in write action
 * 4. [PsiDocumentManager.allowIsolatedCommits] is entered
 * 5. [document] is committed -- this is a lightweight commit
 * 6. [assertCommonPsi] is invoked on the [PsiFile] of [document] to check language-specific properties
 * 7. [LightweightCommitScenario.assertPsi] is invoked on the obtained [PsiFile]
 * 8. [PsiDocumentManager.allowIsolatedCommits] is exited
 */
@TestOnly
suspend inline fun <reified F : PsiFile> assertLightweightCommitScenario(
  project: Project,
  document: Document,
  baseText: String,
  scenario: LightweightCommitScenario<F>,
  noinline assertCommonPsi: (F) -> Unit = {},
) {
  assertLightweightCommitScenario(project, document, baseText, scenario, F::class.java, assertCommonPsi)
}

/**
 * Asserts that every PSI root of the view provider of [psiFile] is text-consistent with [document].
 *
 * Meaningful for languages with a [com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider], where a single
 * document backs several PSI roots, and each root has to be reparsed by a lightweight commit.
 */
@TestOnly
fun assertViewProviderRootsMatchDocument(document: Document, psiFile: PsiFile) {
  val documentText = document.immutableCharSequence.toString()
  val viewProvider = psiFile.viewProvider
  Assertions.assertEquals(documentText, viewProvider.contents.toString(),
                          "View provider contents of ${psiFile.name} do not match the document")
  val roots = viewProvider.allFiles
  Assertions.assertTrue(roots.isNotEmpty(), "View provider of ${psiFile.name} has no PSI roots")
  for (root in roots) {
    Assertions.assertEquals(documentText, root.text,
                            "PSI root ${root.language.id} of ${psiFile.name} is inconsistent with the document")
  }
}

/**
 * Replaces everything between [startMarker] and [endMarker] with [replacement], keeping both markers.
 *
 * Used to derive scenario texts from a single base text via marker comments.
 */
@TestOnly
fun String.replaceBetween(startMarker: String, endMarker: String, replacement: String): String {
  val start = indexOf(startMarker)
  require(start >= 0) { "Cannot find start marker: $startMarker" }
  val contentStart = start + startMarker.length
  val end = indexOf(endMarker, contentStart)
  require(end >= 0) { "Cannot find end marker: $endMarker" }
  return substring(0, contentStart) + replacement + substring(end)
}

@TestOnly
private fun currentPsiFile(project: Project, document: Document): PsiFile {
  return PsiDocumentManager.getInstance(project).getPsiFile(document) ?: error("PSI file should exist for $document")
}

@TestOnly
private suspend fun resetToBaseText(project: Project, document: Document, baseText: String) {
  writeCommandAction(project, "") {
    document.setText(baseText)
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }
  readAction {
    val psiFile = currentPsiFile(project, document)
    PsiTestUtil.checkFileStructure(psiFile)
    Assertions.assertEquals(baseText, psiFile.text)
  }
}
