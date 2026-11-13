// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * The fixture of the workspace model inspection tests. Create it with [workspaceInspectionFixture].
 *
 * The project has the stubs of the workspace storage API. The test data of a test is in `<test name>/entity.kt`,
 * and the expected result of a quick fix is in `<test name>/entity_after.kt`.
 */
class WorkspaceInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  private fun getBeforeAndAfterFileNames(): Pair<String, String> {
    val testName = fixture.getTestName(true)
    val fileNameBefore = "$testName/entity.kt"
    val fileNameAfter = "${testName}/entity_after.kt"
    return fileNameBefore to fileNameAfter
  }

  /**
   * Checks the highlighting of the test file, applies the quick fix [fixName] and checks the result.
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTestWithQuickFix(fixName: String) {
    val (fileNameBefore, fileNameAfter) = getBeforeAndAfterFileNames()
    fixture.testHighlighting(fileNameBefore)
    runInEdtAndWait {
      val quickFix = fixture.getAllQuickFixes().find { it.text == fixName }
      assertNotNull(quickFix, "Fix $fixName not found")
      fixture.checkPreviewAndLaunchAction(quickFix!!)
      fixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
    }
  }

  /**
   * Checks the highlighting of the test file.
   */
  fun doTest() {
    val (fileNameBefore, _) = getBeforeAndAfterFileNames()
    fixture.testHighlighting(fileNameBefore)
  }
}

/**
 * Creates the project of a workspace model inspection test class: a project with the Kotlin standard library.
 * Declare it in a companion object, and give it to [workspaceInspectionFixture].
 */
fun workspaceInspectionProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())

/**
 * Creates a [WorkspaceInspectionFixture] for one test. Declare it as an instance field.
 * The test enables the inspection that it checks.
 */
fun workspaceInspectionFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<WorkspaceInspectionFixture> =
  testFixture("workspaceInspectionFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture, TESTDATA_PATH).init()
    addKotlinFile(fixture, "EntitySource.kt", """
      package com.intellij.platform.workspace.storage
      interface EntitySource
    """.trimIndent())
    addKotlinFile(fixture, "Abstract.kt", """
      package com.intellij.platform.workspace.storage.annotations
      @Target(AnnotationTarget.CLASS)
      annotation class Abstract
    """.trimIndent())
    addKotlinFile(fixture, "generatedCodeCompatibility.kt", """
      package com.intellij.platform.workspace.storage
      object CodeGeneratorVersions {
        private const val API_VERSION_INTERNAL = 3
      }
      @Target(AnnotationTarget.CLASS)
      @Retention(AnnotationRetention.RUNTIME)
      annotation class GeneratedCodeApiVersion(val version: Int)
    """.trimIndent())
    addKotlinFile(fixture, "WorkspaceEntity.kt", """
      package com.intellij.platform.workspace.storage
      import com.intellij.platform.workspace.storage.annotations.Abstract
      @Abstract
      interface WorkspaceEntity
    """.trimIndent())
    addKotlinFile(fixture, "MetadataStorageBase.kt", """
      package com.intellij.platform.workspace.storage.metadata.impl
      abstract class MetadataStorageBase() {
        protected fun addMetadataHash(typeFqn: String, metadataHash: Int) {}
        protected abstract fun initializeMetadataHash()
      }
    """.trimIndent())
    addKotlinFile(fixture, "WorkspaceEntityBase.kt", """
      package com.intellij.platform.workspace.storage.impl
      import com.intellij.platform.workspace.storage.WorkspaceEntity
      abstract class WorkspaceEntityBase() : WorkspaceEntity
    """.trimIndent())
    initialized(WorkspaceInspectionFixture(fixture)) {}
  }

private fun addKotlinFile(fixture: LightJavaCodeInsightTestFixture, relativePath: String, @Language("kotlin") fileText: String) {
  fixture.addFileToProject(relativePath, fileText)
}

private val TESTDATA_PATH =
  PluginPathManager.getPluginHomePathRelative("devkit") + "/intellij.devkit.workspaceModel/tests/testData/inspections/"
