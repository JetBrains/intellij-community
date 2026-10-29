package com.intellij.grazie.markdown.specification.inspection

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class SpecificationContradictionInspectionTest : BasePlatformTestCase() {
  private var cacheResets = 0

  override fun getTestDataPath(): String = "${PathManager.getHomePath()}/community/plugins/grazie/markdown/specification/test/testData"

  fun testSelectsExactlyTwoRegistrationFilesInBothDirections() {
    val frontend = myFixture.addFileToProject(
      "frontend/AGENTS.md",
      """
        # Frontend module registration

        No JPS module may be registered with `jps-module.mjs`.
        Register modules by editing `modules.xml` by hand.
        Do not use the helper to update the `.iml` file.
      """.trimIndent(),
    )
    val backend = myFixture.addFileToProject(
      "backend/AGENTS.md",
      """
        # Backend module registration

        All JPS modules must be registered with `jps-module.mjs`.
        The helper updates the `.iml` file and `modules.xml`.
        Never edit `modules.xml` by hand.
      """.trimIndent(),
    )
    val inspection = SpecificationContradictionInspection()

    assertEquals(
      setOf(relativePath(frontend), relativePath(backend)),
      dependencyPaths(inspection.getDependencies(frontend)),
    )
    assertEquals(
      setOf(relativePath(frontend), relativePath(backend)),
      dependencyPaths(inspection.getDependencies(backend)),
    )
  }

  fun testSimilarFilesShareOneSet() {
    val fileA = myFixture.addFileToProject("a/AGENTS.md", "bridgeab alphaonly")
    val fileB = myFixture.addFileToProject("b/AGENTS.md", "bridgeab bridgebc")
    val fileC = myFixture.addFileToProject("c/AGENTS.md", "bridgebc gammaonly")
    val expected = setOf(relativePath(fileA), relativePath(fileB), relativePath(fileC))

    assertEquals(expected, computeDependencies(fileC))
    assertEquals(expected, computeDependencies(fileA))
    assertEquals(expected, computeDependencies(fileB))
  }

  fun testSplitsWordsAsTheWordIndex() {
    val fileA = myFixture.addFileToProject("a/AGENTS.md", "bridgeword alphaonly")
    val fileB = myFixture.addFileToProject("b/AGENTS.md", "bridgeword_tail gammaonly")

    assertEquals(setOf(relativePath(fileA)), computeDependencies(fileA))
    assertEquals(setOf(relativePath(fileB)), computeDependencies(fileB))
  }

  fun testUpdatesDependenciesAfterCandidateEditAndLinkAddition() {
    myFixture.copyDirectoryToProject("specification-contradiction-test", "specification-contradiction-test")
    val noise = myFixture.addFileToProject("noise/SKILL.md", "Recipes for making soup and baking bread.")
    val editableCandidate = myFixture.addFileToProject("candidate/AGENTS.md", "Recipes for making soup and baking bread.")
    val inspection = SpecificationContradictionInspection()
    val backend = findPsiFile("specification-contradiction-test/backend/AGENTS.md")
    val frontend = findPsiFile("specification-contradiction-test/frontend/AGENTS.md")

    val linkedFile = myFixture.addFileToProject(
      "specification-contradiction-test/backend/database/SKILL.md",
      "Database schema migrations.",
    )
    val backendDependencies = dependencyPaths(inspection.getDependencies(backend))
    assertTrue(backendDependencies.contains("specification-contradiction-test/frontend/AGENTS.md"))
    assertFalse(backendDependencies.contains(relativePath(noise)))
    assertFalse(backendDependencies.contains(relativePath(editableCandidate)))
    assertFalse(backendDependencies.contains(relativePath(linkedFile)))

    val frontendDependencies = dependencyPaths(inspection.getDependencies(frontend))
    assertTrue(frontendDependencies.contains("specification-contradiction-test/frontend/SKILL.md"))
    assertTrue(frontendDependencies.contains("specification-contradiction-test/backend/AGENTS.md"))

    myFixture.openFileInEditor(editableCandidate.virtualFile)
    myFixture.editor.caretModel.moveToOffset(editableCandidate.textLength)
    myFixture.type(" All JPS modules must be registered with jps-module.mjs. The helper updates the .iml file and modules.xml.")
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val dependenciesAfterCandidateEdit = dependencyPaths(
      inspection.getDependencies(findPsiFile("specification-contradiction-test/backend/AGENTS.md")),
    )
    assertTrue(dependenciesAfterCandidateEdit.contains(relativePath(editableCandidate)))

    myFixture.openFileInEditor(backend.virtualFile)
    myFixture.editor.caretModel.moveToOffset(backend.textLength)
    myFixture.type("\nSee [database details](database/SKILL.md).")
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val dependenciesAfterLink = dependencyPaths(
      inspection.getDependencies(findPsiFile("specification-contradiction-test/backend/AGENTS.md")),
    )
    assertTrue(dependenciesAfterLink.contains(relativePath(linkedFile)))

    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
    myFixture.type(" Additional backend wording.")
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    assertEquals(
      dependenciesAfterLink,
      dependencyPaths(inspection.getDependencies(findPsiFile("specification-contradiction-test/backend/AGENTS.md"))),
    )
  }

  /** Computes the dependency set again. A new file changes the VFS structure, and this clears the cache. */
  private fun computeDependencies(file: PsiFile): Set<String> {
    myFixture.addFileToProject("cache-reset-${cacheResets++}.txt", "")
    return dependencyPaths(SpecificationContradictionInspection().getDependencies(file))
  }

  private fun findPsiFile(path: String): PsiFile {
    val virtualFile = myFixture.findFileInTempDir(path) ?: error("File not found: $path")
    return PsiManager.getInstance(project).findFile(virtualFile) ?: error("PSI file not found: $path")
  }

  private fun dependencyPaths(files: Set<PsiFile>): Set<String> = files.mapTo(HashSet(), ::relativePath)

  private fun relativePath(file: PsiFile): String = relativePath(file.virtualFile)

  private fun relativePath(file: VirtualFile): String =
    file.path.removePrefix("/src/")
}
