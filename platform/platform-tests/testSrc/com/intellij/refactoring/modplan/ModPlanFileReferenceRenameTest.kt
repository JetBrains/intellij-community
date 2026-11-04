// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ProcessingContext

/**
 * The rename of a file or a directory that a [com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference] names. Each
 * case runs once with the legacy rename and once on the ModPlan engine, and both give the same text.
 */
class ModPlanFileReferenceRenameTest : BasePlatformTestCase() {
  /** Gives the file references of a path to each value of the attribute `path`. */
  class PathReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
      registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue("path"), object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
          arrayOf(*FileReferenceSet(element).allReferences)
      })
    }
  }

  override fun setUp() {
    super.setUp()
    PsiReferenceContributor.EP_NAME.point.registerExtension(
      PsiReferenceContributorEP().apply {
        language = "XML"
        implementationClass = PathReferenceContributor::class.java.name
        pluginDescriptor = DefaultPluginDescriptor("intellij.platform.tests.modPlanFileReferenceRename")
      },
      testRootDisposable,
    )
  }

  private class PathData(val target: PsiFile, val inDirectory: PsiFile, val inRoot: PsiFile)

  private fun createPathData(): PathData {
    val target = myFixture.addFileToProject("dir/a.txt", "a")
    val inDirectory = myFixture.addFileToProject("dir/near.xml", """<root><e path="a.txt"/><e path="./a.txt"/></root>""")
    val inRoot = myFixture.addFileToProject("far.xml", """<root><e path="dir/a.txt"/></root>""")
    return PathData(target, inDirectory, inRoot)
  }

  private fun renameOnModPlan(target: PsiElement, newName: String) {
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, target, newName, true, ModPlanOptionValues.EMPTY) }
    val plan = inBackground { ModPlanRenameRefactoring.getInstance().plan(request) }
    assertTrue(plan.toString(), plan is ModPlan.AcceptedPlan)
    val acceptedPlan = plan as ModPlan.AcceptedPlan
    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(acceptedPlan.execution, true, acceptedPlan.writeEvents))
  }

  private fun textOf(file: PsiFile): String = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!.text

  private fun assertFileRenamed(data: PathData) {
    assertEquals("b.txt", data.target.name)
    assertEquals("""<root><e path="b.txt"/><e path="b.txt"/></root>""", textOf(data.inDirectory))
    assertEquals("""<root><e path="dir/b.txt"/></root>""", textOf(data.inRoot))
  }

  private fun assertDirectoryRenamed(data: PathData) {
    assertEquals("folder", data.target.containingDirectory.name)
    assertEquals("""<root><e path="a.txt"/><e path="./a.txt"/></root>""", textOf(data.inDirectory))
    assertEquals("""<root><e path="folder/a.txt"/></root>""", textOf(data.inRoot))
  }

  fun testLegacyRenameOfAFileBindsEachPath() {
    val data = createPathData()

    myFixture.renameElement(data.target, "b.txt", false, false)

    assertFileRenamed(data)
  }

  fun testFileReferencesAreBoundAndRenamedAsTheLegacyRenameDoes() {
    val data = createPathData()

    renameOnModPlan(data.target, "b.txt")

    assertFileRenamed(data)
  }

  fun testLegacyRenameOfADirectoryInTheMiddleOfAPathBindsThePath() {
    val data = createPathData()

    myFixture.renameElement(data.target.containingDirectory, "folder", false, false)

    assertDirectoryRenamed(data)
  }

  fun testDirectoryInTheMiddleOfAPathIsRenamedAsTheLegacyRenameDoes() {
    val data = createPathData()

    renameOnModPlan(data.target.containingDirectory, "folder")

    assertDirectoryRenamed(data)
  }
}
