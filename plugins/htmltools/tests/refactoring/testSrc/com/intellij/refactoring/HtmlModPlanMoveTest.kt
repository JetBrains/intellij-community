// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.lang.html.HTMLLanguage
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.XmlPatterns
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.sharedCodeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ProcessingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@TestApplication
class HtmlModPlanMoveTest {
  companion object {
    private val projectFixture = sharedCodeInsightProjectFixture()
  }

  private val myFixture by sharedCodeInsightFixture(projectFixture)

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project: Project
    get() = myFixture.project

  private fun root(): VirtualFile = myFixture.tempDirFixture.getFile("")!!

  private fun accepted(
    paths: List<String>,
    targetPath: String,
    optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
  ): ModPlan.AcceptedPlan {
    val root = root()
    val files = paths.map { root.findFileByRelativePath(it)!! }
    val plan = ReadAction.computeBlocking<ModPlan, Throwable> {
      val psiManager = PsiManager.getInstance(project)
      val items = files.map<VirtualFile, PsiFileSystemItem> {
        if (it.isDirectory) psiManager.findDirectory(it)!! else psiManager.findFile(it)!!
      }
      val request = ModPlanMoveRefactoring.request(project, items, root.url + "/" + targetPath, optionValues)
      ModPlanMoveRefactoring.getInstance().plan(request)
    }
    return plan as ModPlan.AcceptedPlan
  }

  private fun execute(plan: ModPlan.AcceptedPlan): ModPlanExecutionResult =
    runInEdtAndGet { ModPlanExecutor.execute(plan.execution, true, plan.writeEvents) }

  private fun move(paths: List<String>, targetPath: String): ModPlanExecutionResult = execute(accepted(paths, targetPath))

  private fun documentOf(path: String): Document {
    val file = root().findFileByRelativePath(path)!!
    return ReadAction.computeBlocking<Document, Throwable> { FileDocumentManager.getInstance().getDocument(file)!! }
  }

  private fun textOf(path: String): String = documentOf(path).text

  private fun resolvedTargetOf(path: String, referenceText: String): VirtualFile? {
    val file = root().findFileByRelativePath(path)!!
    return ReadAction.computeBlocking<VirtualFile?, Throwable> {
      val psiFile = PsiManager.getInstance(project).findFile(file)!!
      val target = psiFile.findReferenceAt(psiFile.text.indexOf(referenceText))?.resolve()
      (target as? PsiFileSystemItem)?.virtualFile
    }
  }

  private fun hasTextUpdates(plan: ModPlan.AcceptedPlan): Boolean = plan.execution.command.unpack().any { it is ModUpdateFileText }

  private fun withoutSearchForReferences(): ModPlanOptionValues =
    ModPlanOptionValues.EMPTY.with(ModPlanMoveOptions.SEARCH_FOR_REFERENCES, false)

  @Test
  fun `test a reference to a moved file gets the new relative path`() {
    myFixture.addFileToProject("page.html", """<a href="img/a.png">image</a>""")
    myFixture.addFileToProject("img/a.png", "")

    val result = move(listOf("img/a.png"), "assets")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<a href="assets/a.png">image</a>""", textOf("page.html"))
    assertNotNull(root().findFileByRelativePath("assets/a.png"))
  }

  @Test
  fun `test the places of the updated references are the usages of the plan`() {
    myFixture.addFileToProject("page.html", """<a href="img/a.png">image</a>""")
    myFixture.addFileToProject("img/a.png", "")

    val pageText = textOf("page.html")

    val plan = accepted(listOf("img/a.png"), "assets")

    val usageTexts = ReadAction.computeBlocking<List<String>, Throwable> {
      plan.usages.map { usage -> usage.range!!.let { pageText.substring(it.startOffset, it.endOffset) } }
    }
    assertEquals(listOf("img/a.png"), usageTexts)
    assertEquals(root().findChild("page.html"), plan.usages.single().virtualFile)
  }

  @Test
  fun `test an excluded reference keeps its path`() {
    val pageText = """<a href="img/a.png">image</a><img src="img/a.png">"""
    myFixture.addFileToProject("page.html", pageText)
    myFixture.addFileToProject("img/a.png", "")
    val usages = accepted(listOf("img/a.png"), "assets").usages
    val excludedUsage = ReadAction.computeBlocking<String, Throwable> {
      ModPlanRefactoring.excludedUsagesOf(usages.filter { it.range!!.startOffset == pageText.indexOf("img/a.png") })
    }

    val plan = accepted(listOf("img/a.png"), "assets", ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.EXCLUDED_USAGES, excludedUsage))

    assertEquals(1, plan.usages.size)
    assertEquals(ModPlanExecutionResult.Applied, execute(plan))
    assertEquals("""<a href="img/a.png">image</a><img src="assets/a.png">""", textOf("page.html"))
    assertNotNull(root().findFileByRelativePath("assets/a.png"))
  }

  @Test
  fun `test a reference to a moved directory keeps the ending slash`() {
    myFixture.addFileToProject("page.html", """<a href="docs/">docs</a>""")
    myFixture.addFileToProject("docs/index.html", "")

    val result = move(listOf("docs"), "site")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<a href="site/docs/">docs</a>""", textOf("page.html"))
  }

  @Test
  fun `test two references in one attribute get the new paths`() {
    myFixture.addFileToProject("page.html", """<img srcset="a.png 1x, b.png 2x">""")
    myFixture.addFileToProject("a.png", "")
    myFixture.addFileToProject("b.png", "")

    val result = move(listOf("a.png", "b.png"), "img")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<img srcset="img/a.png 1x, img/b.png 2x">""", textOf("page.html"))
  }

  @Test
  fun `test a reference to a file inside a moved directory gets the new relative path`() {
    myFixture.addFileToProject("page.html", """<a href="docs/index.html">docs</a>""")
    myFixture.addFileToProject("docs/index.html", "")

    val result = move(listOf("docs"), "site")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<a href="site/docs/index.html">docs</a>""", textOf("page.html"))
  }

  @Test
  fun `test a reference in a moved file gets the new relative path`() {
    myFixture.addFileToProject("a.png", "")
    myFixture.addFileToProject("d/page.html", """<img src="../a.png">""")

    val result = move(listOf("d/page.html"), "e/f")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<img src="../../a.png">""", textOf("e/f/page.html"))
  }

  @Test
  fun `test a reference in a file inside a moved directory gets the new relative path`() {
    myFixture.addFileToProject("a.png", "")
    myFixture.addFileToProject("d/page.html", """<img src="../a.png">""")

    val result = move(listOf("d"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<img src="../../a.png">""", textOf("e/d/page.html"))
  }

  @Test
  fun `test a path through the old directory of the moved files gets the new path`() {
    myFixture.addFileToProject("d/page.html", """<img src="../d/a.png">""")
    myFixture.addFileToProject("d/a.png", "")

    val result = move(listOf("d/page.html", "d/a.png"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<img src="a.png">""", textOf("e/page.html"))
  }

  @Test
  fun `test references between items that move together stay`() {
    myFixture.addFileToProject("d/page.html", """<img src="a.png">""")
    myFixture.addFileToProject("d/a.png", "")

    val plan = accepted(listOf("d"), "e")

    assertEquals(listOf<Any>(), plan.conflicts.conflicts)
    assertFalse(hasTextUpdates(plan))
    assertEquals(ModPlanExecutionResult.Applied, execute(plan))
    assertEquals("""<img src="a.png">""", textOf("e/d/page.html"))
    assertEquals(root().findFileByRelativePath("e/d/a.png"), resolvedTargetOf("e/d/page.html", "a.png"))
  }

  @Test
  fun `test a root-based reference in a moved file stays without conflict`() {
    myFixture.addFileToProject("css/site.css", "")
    myFixture.addFileToProject("d/page.html", """<link rel="stylesheet" href="/css/site.css">""")
    val styleSheet = root().findFileByRelativePath("css/site.css")
    assertEquals(styleSheet, resolvedTargetOf("d/page.html", "site.css"))

    val plan = accepted(listOf("d/page.html"), "e")

    assertEquals(listOf<Any>(), plan.conflicts.conflicts)
    assertFalse(hasTextUpdates(plan))
    assertEquals(ModPlanExecutionResult.Applied, execute(plan))
    assertEquals("""<link rel="stylesheet" href="/css/site.css">""", textOf("e/page.html"))
    assertEquals(styleSheet, resolvedTargetOf("e/page.html", "site.css"))
  }

  @Test
  fun `test a root-based reference in a file moved out of its root is a conflict`() {
    myFixture.addFileToProject("web/css/site.css", "")
    myFixture.addFileToProject("web/d/page.html", """<link rel="stylesheet" href="/css/site.css">""")
    val webRoot = root().findChild("web")!!
    runInEdtAndWait { PsiTestUtil.addSourceRoot(myFixture.module, webRoot) }
    try {
      assertEquals(root().findFileByRelativePath("web/css/site.css"), resolvedTargetOf("web/d/page.html", "site.css"))

      val plan = accepted(listOf("web/d/page.html"), "e")

      val conflict = plan.conflicts.conflicts.single()
      assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
      assertEquals(root().findFileByRelativePath("web/d/page.html"), conflict.location?.virtualFile)
      val message = RefactoringBundle.message("move.modplan.conflict.moved.reference.not.updated",
                                              CommonRefactoringUtil.htmlEmphasize("/css/site.css"))
      assertEquals(listOf(message), conflict.messages)
      assertFalse(hasTextUpdates(plan))
    }
    finally {
      runInEdtAndWait { PsiTestUtil.removeSourceRoot(myFixture.module, webRoot) }
    }
  }

  @Disabled("A false BROKEN conflict: keepsRoot checks only the deepest root that contains the file, not every root of the module")
  @Test
  fun `test a root-based reference in a file moved out of its root within its module stays without conflict`() {
    myFixture.addFileToProject("web/css/site.css", "")
    myFixture.addFileToProject("web/d/page.html", """<link rel="stylesheet" href="/css/site.css">""")
    val webRoot = root().findChild("web")!!
    runInEdtAndWait { PsiTestUtil.addSourceRoot(myFixture.module, webRoot) }
    try {
      val styleSheet = root().findFileByRelativePath("web/css/site.css")
      assertEquals(styleSheet, resolvedTargetOf("web/d/page.html", "site.css"))

      val plan = accepted(listOf("web/d/page.html"), "e")

      assertEquals(listOf<Any>(), plan.conflicts.conflicts)
      assertFalse(hasTextUpdates(plan))
      assertEquals(ModPlanExecutionResult.Applied, execute(plan))
      assertEquals("""<link rel="stylesheet" href="/css/site.css">""", textOf("e/page.html"))
      assertEquals(styleSheet, resolvedTargetOf("e/page.html", "site.css"))
    }
    finally {
      runInEdtAndWait { PsiTestUtil.removeSourceRoot(myFixture.module, webRoot) }
    }
  }

  @Test
  fun `test a root-based reference to a moved file is a conflict`() {
    myFixture.addFileToProject("css/site.css", "")
    myFixture.addFileToProject("page.html", """<link rel="stylesheet" href="/css/site.css">""")
    assertEquals(root().findFileByRelativePath("css/site.css"), resolvedTargetOf("page.html", "site.css"))

    val plan = accepted(listOf("css/site.css"), "styles")

    val conflict = plan.conflicts.conflicts.single()
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(root().findFileByRelativePath("page.html"), conflict.location?.virtualFile)
    assertFalse(hasTextUpdates(plan))
  }

  @Test
  fun `test a reference that is not a file reference to a moved file is a conflict`() {
    PsiReferenceContributor.EP_NAME.point.registerExtension(PsiReferenceContributorEP().apply {
      language = HTMLLanguage.INSTANCE.id
      implementationClass = DataFileReferenceContributor::class.java.name
      pluginDescriptor = DefaultPluginDescriptor("html-modplan-move-test")
    }, disposable)
    myFixture.addFileToProject("page.html", """<div data-file="a.png"></div>""")
    myFixture.addFileToProject("a.png", "")
    assertEquals(root().findChild("a.png"), resolvedTargetOf("page.html", "a.png"))

    val plan = accepted(listOf("a.png"), "img")

    val conflict = plan.conflicts.conflicts.single()
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(root().findChild("page.html"), conflict.location?.virtualFile)
    val message = RefactoringBundle.message("move.modplan.conflict.reference.not.updated", CommonRefactoringUtil.htmlEmphasize("a.png"))
    assertEquals(listOf(message), conflict.messages)
    assertFalse(hasTextUpdates(plan))
  }

  @Test
  fun `test a move without the search for references keeps the references to the moved file`() {
    myFixture.addFileToProject("page.html", """<a href="img/a.png">image</a>""")
    myFixture.addFileToProject("img/a.png", "")

    val plan = accepted(listOf("img/a.png"), "assets", withoutSearchForReferences())

    assertEquals(listOf<Any>(), plan.usages)
    assertEquals(ModPlanExecutionResult.Applied, execute(plan))
    assertEquals("""<a href="img/a.png">image</a>""", textOf("page.html"))
    assertNotNull(root().findFileByRelativePath("assets/a.png"))
  }

  @Test
  fun `test a move without the search for references keeps the references in the moved file`() {
    myFixture.addFileToProject("a.png", "")
    myFixture.addFileToProject("d/page.html", """<img src="../a.png">""")

    val result = execute(accepted(listOf("d/page.html"), "e/f", withoutSearchForReferences()))

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("""<img src="../a.png">""", textOf("e/f/page.html"))
  }

  @Test
  fun `test a move without the search for references reports no conflict of a reference`() {
    myFixture.addFileToProject("css/site.css", "")
    myFixture.addFileToProject("page.html", """<link rel="stylesheet" href="/css/site.css">""")

    val plan = accepted(listOf("css/site.css"), "styles", withoutSearchForReferences())

    assertEquals(listOf<Any>(), plan.conflicts.conflicts)
  }

  @Test
  fun `test unsaved text of a moved file is kept`() {
    myFixture.addFileToProject("a.png", "")
    myFixture.addFileToProject("d/page.html", """<img src="../a.png">""")
    val document = documentOf("d/page.html")
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        document.insertString(document.textLength, "<p>unsaved</p>")
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    val result = move(listOf("d/page.html"), "e/f")

    assertEquals(ModPlanExecutionResult.Applied, result)
    val movedFile = root().findFileByRelativePath("e/f/page.html")!!
    assertEquals("""<img src="../../a.png"><p>unsaved</p>""", textOf("e/f/page.html"))
    assertTrue(FileDocumentManager.getInstance().isFileModified(movedFile))
  }

  /** A contributor of a reference from the `data-file` attribute to a file of the same directory. The reference is not a file reference. */
  class DataFileReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
      registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName("data-file"), object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
          arrayOf(DataFileReference(element as XmlAttributeValue))
      })
    }
  }

  private class DataFileReference(element: XmlAttributeValue) : PsiReferenceBase<XmlAttributeValue>(element) {
    override fun resolve(): PsiElement? = element.containingFile.originalFile.containingDirectory?.findFile(element.value)
  }
}