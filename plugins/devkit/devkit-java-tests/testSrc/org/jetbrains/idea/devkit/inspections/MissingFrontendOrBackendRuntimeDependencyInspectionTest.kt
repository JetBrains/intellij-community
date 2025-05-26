// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
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

  private fun testReportDependency(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <idea-plugin>
        <<error descr="The current module ('intellij.test.feature.$frontendOrBackend') is a .$frontendOrBackend module, but the dependency on 'intellij.platform.$frontendOrBackend' is missing">dependencies</error>>
          <!-- no dependency -->
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testReportDependencyWhenDependenciesElementDoesNotExist(frontendOrBackend: String) {
    val testedFile = myFixture.addXmlFile("intellij.test.feature.$frontendOrBackend.xml", """
      <<error descr="The current module ('intellij.test.feature.$frontendOrBackend') is a .$frontendOrBackend module, but the dependency on 'intellij.platform.$frontendOrBackend' is missing">idea-plugin</error>>
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
        <<error descr="The current module ('intellij.test.cyclic.$frontendOrBackend') is a .$frontendOrBackend module, but the dependency on 'intellij.platform.$frontendOrBackend' is missing">dependencies</error>>
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
}

internal class MissingFrontendOrBackendRuntimeDependencyFixTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MissingFrontendOrBackendRuntimeDependencyInspection())
  }

  fun testAddingFrontendDependency() = testAddingDependency("frontend")
  fun testAddingBackendDependency() = testAddingDependency("backend")

  fun testAddingFrontendDependencyWhenDependenciesElementDoesNotExist() = testAddingDependencyWhenDependenciesElementDoesNotExist("frontend")
  fun testAddingBackendDependencyWhenDependenciesElementDoesNotExist() = testAddingDependencyWhenDependenciesElementDoesNotExist("backend")

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
      """.trimIndent(), """
      <idea-plugin>
          <dependencies>
              <!-- no dependency -->
              <module name="intellij.platform.$frontendOrBackend"/>
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
      """.trimIndent(), """
      <idea-plugin>
          <!-- no dependency -->
          <dependencies>
              <module name="intellij.platform.$frontendOrBackend"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
  }

  private fun doTest(
    frontendOrBackend: String,
    fileName: String,
    @Language("XML") before: String,
    @Language("XML") after: String,
  ) {
    val testedFile = myFixture.addXmlFile(fileName, before)
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    val intention = myFixture.findSingleIntention("Add the 'intellij.platform.$frontendOrBackend' dependency")
    myFixture.checkPreviewAndLaunchAction(intention)
    myFixture.checkResult(after, true)
  }

}

private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
  return this.addFileToProject(relativePath, fileText)
}
