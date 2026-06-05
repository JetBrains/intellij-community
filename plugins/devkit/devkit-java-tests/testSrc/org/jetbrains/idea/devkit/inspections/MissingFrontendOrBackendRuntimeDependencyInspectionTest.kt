// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.remotedev.EXCLUSIONS_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.MissingFrontendOrBackendRuntimeDependencyInspection
import org.jetbrains.idea.devkit.module.PluginModuleType

internal class MissingFrontendOrBackendRuntimeDependencyInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MissingFrontendOrBackendRuntimeDependencyInspection())
  }

  fun testReportFrontendDependency() = testReportDependency("frontend")
  fun testReportBackendDependency() = testReportDependency("backend")

  fun testReportFrontendDependencyWhenDependenciesElementDoesNotExist() =
    testReportDependencyWhenDependenciesElementDoesNotExist("frontend")

  fun testReportBackendDependencyWhenDependenciesElementDoesNotExist() = testReportDependencyWhenDependenciesElementDoesNotExist("backend")

  fun testShouldNotReportCorrectFrontendDependency() = testShouldNotReportCorrectDependency("frontend")
  fun testShouldNotReportCorrectBackendDependency() = testShouldNotReportCorrectDependency("backend")

  fun testShouldNotReportCorrectTransitiveFrontendDependency() = testShouldNotReportCorrectTransitiveDependency("frontend")
  fun testShouldNotReportCorrectTransitiveBackendDependency() = testShouldNotReportCorrectTransitiveDependency("backend")

  fun testShouldNotReportInCoreFrontendModule() = testShouldNotReportInCoreModule("frontend")
  fun testShouldNotReportInCoreBackendModule() = testShouldNotReportInCoreModule("backend")

  fun testShouldNotReportInNotIntellijFrontendModule() = testShouldNotReportInNotIntellijModule("frontend")
  fun testShouldNotReportInNotIntellijBackendModule() = testShouldNotReportInNotIntellijModule("backend")

  fun testShouldNotReportInFrontendModuleInPluginXml() = testShouldNotReportInPluginXml("frontend")
  fun testShouldNotReportInBackendModuleInPluginXml() = testShouldNotReportInPluginXml("backend")

  fun testShouldNotCrashOnCyclicDependencyInFrontend() = testShouldNotCrashOnCyclicDependency("frontend")
  fun testShouldNotCrashOnCyclicDependencyInBackend() = testShouldNotCrashOnCyclicDependency("backend")

  fun testShouldNotReportExcludedFrontendDependency() {
    myFixture.addSplitModeExclusionsFile(splitModeExclusionsJson(
      missingRuntimeDependencyExclusion("intellij.test.feature.frontend.xml", 2)
    ))

    val testedFile = myFixture.addXmlFile("intellij.test.feature.frontend.xml", """
      <idea-plugin>
        <dependencies>
          <!-- no dependency -->
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun testShouldReportWhenExclusionsFileIsEmpty() {
    myFixture.addSplitModeExclusionsFile("")
    testReportDependency("frontend")
  }

  fun testExclusionsInvalidatedAfterJsonChange() {
    val exclusionsFile = myFixture.addSplitModeExclusionsFile("""{ "exclusions": [] }""")
    val testedFile = myFixture.addXmlFile("intellij.test.feature.frontend.xml", """
      <idea-plugin>
        <dependencies>
          <!-- no dependency -->
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    assertTrue(hasMissingFrontendRuntimeDependencyWarning(testedFile))

    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(exclusionsFile.virtualFile, splitModeExclusionsJson(
        missingRuntimeDependencyExclusion("intellij.test.feature.frontend.xml", 2)
      ))
    }

    assertFalse(hasMissingFrontendRuntimeDependencyWarning(testedFile))
  }

  private fun testReportDependency(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <idea-plugin>
        <<error descr="The name of the current module 'intellij.test.feature.$frontendOrBackend' ends with '.$frontendOrBackend', but the dependency on 'intellij.platform.$frontendOrBackend' is missing">dependencies</error>>
          <!-- no dependency -->
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testReportDependencyWhenDependenciesElementDoesNotExist(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <<error descr="The name of the current module 'intellij.test.feature.$frontendOrBackend' ends with '.$frontendOrBackend', but the dependency on 'intellij.platform.$frontendOrBackend' is missing">idea-plugin</error>>
        <!-- no <dependencies> -->
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotReportCorrectDependency(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.$frontendOrBackend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotReportCorrectTransitiveDependency(frontendOrBackend: String) {
    addModule("intellij.transitive.dependency")
    myFixture.addXmlFile("intellij.transitive.dependency/intellij.transitive.dependency.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.$frontendOrBackend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.transitive.dependency"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotReportInCoreModule(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.platform.$frontendOrBackend.xml", """
      <idea-plugin>
          <!-- no dependency -->
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotReportInNotIntellijModule(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("notintellij.platform.$frontendOrBackend.xml", """
      <idea-plugin>
          <!-- no dependency -->
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotReportInPluginXml(frontendOrBackend: String) {
    addModule("intellij.test.$frontendOrBackend")
    val testedFile = myFixture.addXmlFile("intellij.platform.$frontendOrBackend/plugin.xml", """
      <idea-plugin>
        <dependencies>
          <!-- no dependency -->
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testShouldNotCrashOnCyclicDependency(frontendOrBackend: String) {
    addModule("intellij.transitive.dependency")
    myFixture.addXmlFile("intellij.transitive.dependency/intellij.transitive.dependency.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.test.cyclic.$$frontendOrBackend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    val testedFile = myFixture.addXmlFile("intellij.test.cyclic.$frontendOrBackend/intellij.test.cyclic.$frontendOrBackend.xml", """
      <idea-plugin>
        <<error descr="The name of the current module 'intellij.test.cyclic.$frontendOrBackend' ends with '.$frontendOrBackend', but the dependency on 'intellij.platform.$frontendOrBackend' is missing">dependencies</error>>
          <module name="intellij.transitive.dependency"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun addModule(moduleName: String) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
  }

  private fun testHighlighting(testedFile: PsiFile) {
    myFixture.testHighlighting(true, true, true, testedFile.virtualFile)
  }

  private fun hasMissingFrontendRuntimeDependencyWarning(testedFile: PsiFile): Boolean {
    val expectedDescription = "The name of the current module 'intellij.test.feature.frontend' ends with '.frontend', " +
                              "but the dependency on 'intellij.platform.frontend' is missing"
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    return myFixture.doHighlighting().any { it.description == expectedDescription }
  }
}

internal class MissingFrontendOrBackendRuntimeDependencyFixTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MissingFrontendOrBackendRuntimeDependencyInspection())
  }

  fun testAddingFrontendDependency() = testAddingDependency("frontend")
  fun testAddingBackendDependency() = testAddingDependency("backend")

  fun testAddingFrontendDependencyWhenDependenciesElementDoesNotExist() =
    testAddingDependencyWhenDependenciesElementDoesNotExist("frontend")

  fun testAddingBackendDependencyWhenDependenciesElementDoesNotExist() = testAddingDependencyWhenDependenciesElementDoesNotExist("backend")

  fun testAddToExclusionsFixIsHiddenOutsideIntelliJProject() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)

    val testedFile = myFixture.addXmlFile("intellij.test.feature.frontend.xml", """
      <idea-<caret>plugin>
          <!-- no dependency -->
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val splitModeFixes = myFixture.getAllQuickFixes().map { it.text }
    assertFalse(splitModeFixes.contains(addToExclusionsFixName()))
  }

  fun testAddToExclusionsFixIsLastAndAppendsEntry() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)

    val testedFile = myFixture.addXmlFile("intellij.test.feature.frontend.xml", """
      <idea-<caret>plugin>
          <!-- no dependency -->
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val dependencyFixName = addRuntimeDependencyFixName("frontend")
    val exclusionsFixName = addToExclusionsFixName()
    val splitModeFixes = myFixture.getAllQuickFixes()
      .map { it.text }
      .filter { it == dependencyFixName || it == exclusionsFixName }
    assertSameElements(splitModeFixes, dependencyFixName, exclusionsFixName)
    assertEquals(exclusionsFixName, splitModeFixes.last())

    val intention = myFixture.findSingleIntention(exclusionsFixName)
    myFixture.launchAction(intention)

    val exclusionsFile = myFixture.findFileInTempDir(EXCLUSIONS_RELATIVE_PATH)
    val exclusionsText = VfsUtilCore.loadText(exclusionsFile)
    assertTrue(exclusionsText.contains("\"inspection\": \"MissingFrontendOrBackendRuntimeDependency\""))
    assertTrue(exclusionsText.contains("\"file\": \"intellij.test.feature.frontend.xml\""))
    assertTrue(exclusionsText.contains("\"line\": 1"))
    assertTrue(exclusionsText.contains("\"reason\": \"\""))

    val fileEditorManager = FileEditorManager.getInstance(project)
    assertEquals(exclusionsFile, fileEditorManager.selectedEditor?.file)
    val selectedTextEditor = fileEditorManager.selectedTextEditor
    assertNotNull(selectedTextEditor)
    assertEquals(selectedTextEditor!!.document.lineCount - 1, selectedTextEditor.caretModel.logicalPosition.line)
  }

  private fun testAddingDependency(frontendOrBackend: String) {
    doTest(
      frontendOrBackend,
      "intellij.test.feature.$frontendOrBackend.xml",
      """
      <idea-plugin>
          <depend<caret>encies>
              <!-- no dependency -->
          </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
  }

  private fun testAddingDependencyWhenDependenciesElementDoesNotExist(frontendOrBackend: String) {
    doTest(
      frontendOrBackend,
      "intellij.test.feature.$frontendOrBackend.xml",
      """
      <idea-<caret>plugin>
          <!-- no dependency -->
      </idea-plugin>
      """.trimIndent()
    )
  }

  private fun doTest(
    frontendOrBackend: String,
    fileName: String,
    @Language("XML") before: String,
  ) {
    val testedFile = myFixture.addXmlFile(fileName, before)
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    val intention = myFixture.findSingleIntention(addRuntimeDependencyFixName(frontendOrBackend))
    myFixture.checkPreviewAndLaunchAction(intention)

    val fileText = FileDocumentManager.getInstance().getDocument(testedFile.virtualFile)!!.text
    assertTrue(fileText.contains("<!-- no dependency -->"))
    assertTrue(fileText.contains("<dependencies>"))
    assertTrue(fileText.contains("intellij.platform.$frontendOrBackend"))
  }

}

private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
  return this.addFileToProject(relativePath, fileText)
}

private fun CodeInsightTestFixture.addSplitModeExclusionsFile(@Language("JSON") fileText: String): PsiFile {
  return addFileToProject(EXCLUSIONS_RELATIVE_PATH, fileText)
}

private fun splitModeExclusionsJson(entry: String): String {
  return """
    {
      "exclusions": [
        $entry
      ]
    }
    """.trimIndent()
}

private fun missingRuntimeDependencyExclusion(file: String, line: Int, reason: String = "Documented test exclusion"): String {
  return """
    {
      "inspection": "MissingFrontendOrBackendRuntimeDependency",
      "file": "$file",
      "line": $line,
      "reason": "$reason"
    }
    """.trimIndent()
}

private fun addRuntimeDependencyFixName(frontendOrBackend: String): String {
  return "Make module 'intellij.test.feature.$frontendOrBackend' work in '$frontendOrBackend' only"
}

private fun addToExclusionsFixName(): String {
  return "Add this violation to the exclusions list"
}