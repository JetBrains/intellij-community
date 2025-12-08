// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class WorkspaceInspectionBaseTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override fun getProjectDescriptor(): LightProjectDescriptor =
    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  override fun getBasePath() = TESTDATA_PATH

  private fun getBeforeAndAfterFileNames(): Pair<String, String> {
    val testName = getTestName(true)
    val fileNameBefore = "$testName/entity.kt"
    val fileNameAfter = "${testName}/entity_after.kt"
    return fileNameBefore to fileNameAfter
  }

  protected fun addKotlinFile(relativePath: String, @Language("kotlin") fileText: String) {
    myFixture.addFileToProject(relativePath, fileText)
  }

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }

    addKotlinFile("EntitySource.kt", """
      package com.intellij.platform.workspace.storage
      interface EntitySource
    """.trimIndent())
    addKotlinFile("Abstract.kt", """
      package com.intellij.platform.workspace.storage.annotations
      @Target(AnnotationTarget.CLASS)
      annotation class Abstract
    """.trimIndent())
    addKotlinFile("generatedCodeCompatibility.kt", """
      package com.intellij.platform.workspace.storage
      object CodeGeneratorVersions {
        private const val API_VERSION_INTERNAL = 3
      }
      @Target(AnnotationTarget.CLASS)
      @Retention(AnnotationRetention.RUNTIME)
      annotation class GeneratedCodeApiVersion(val version: Int)
    """.trimIndent())
    addKotlinFile("WorkspaceEntity.kt", """
      package com.intellij.platform.workspace.storage
      import com.intellij.platform.workspace.storage.annotations.Abstract
      @Abstract
      interface WorkspaceEntity
    """.trimIndent())
    addKotlinFile("MetadataStorageBase.kt", """
      package com.intellij.platform.workspace.storage.metadata.impl
      abstract class MetadataStorageBase() {
        protected fun addMetadataHash(typeFqn: String, metadataHash: Int) {}
        protected abstract fun initializeMetadataHash()
      }
    """.trimIndent())
    addKotlinFile("WorkspaceEntityBase.kt", """
      package com.intellij.platform.workspace.storage.impl
      import com.intellij.platform.workspace.storage.WorkspaceEntity
      abstract class WorkspaceEntityBase() : WorkspaceEntity
    """.trimIndent())
  }

  protected fun doTestWithQuickFix(fixName: String) {
    val (fileNameBefore, fileNameAfter) = getBeforeAndAfterFileNames()
    myFixture.testHighlighting(fileNameBefore)
    val quickFix = myFixture.getAllQuickFixes().find { it.text == fixName }
    assertNotNull("Fix $fixName not found", quickFix)
    myFixture.checkPreviewAndLaunchAction(quickFix!!)
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
  }

  protected fun doTest() {
    val (fileNameBefore, _) = getBeforeAndAfterFileNames()
    myFixture.testHighlighting(fileNameBefore)
  }
  
  companion object {
    private val TESTDATA_PATH = PluginPathManager.getPluginHomePathRelative("devkit") + "/intellij.devkit.workspaceModel/tests/testData/inspections/"
  }
}