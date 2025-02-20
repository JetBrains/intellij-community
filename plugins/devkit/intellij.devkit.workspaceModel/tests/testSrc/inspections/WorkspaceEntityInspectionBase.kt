// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class WorkspaceEntityInspectionBase: LightJavaCodeInsightFixtureTestCase() {
  private val TESTDATA_PATH = PluginPathManager.getPluginHomePathRelative("devkit") + "/intellij.devkit.workspaceModel/tests/testData/inspections/"

  override fun setUp() {
    super.setUp()
    myFixture.createFile("Obj.kt", """
      package com.intellij.platform.workspace.storage
      
      interface Obj""".trimIndent())
    myFixture.createFile("WorkspaceEntity.kt", """
        package com.intellij.platform.workspace.storage
        
        import com.intellij.platform.workspace.storage.Obj
        
        interface WorkspaceEntity : Obj""".trimIndent())
  }

  override fun getBasePath() = TESTDATA_PATH

  protected open fun doTest(fixName: String) {
    val testName = getTestName(true)
    val fileNameBefore = "$testName/entity.kt"
    val fileNameAfter = "${testName}/entity_after.kt"
    myFixture.testHighlighting(fileNameBefore)
    val intention = myFixture.findSingleIntention(fixName)
    myFixture.checkPreviewAndLaunchAction(intention)
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
  }
}