// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions.generate

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.java.generate.GenerateToStringWorker
import org.jetbrains.java.generate.config.ReplacePolicy
import org.jetbrains.plugins.groovy.actions.GroovyToStringTemplatesManager
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class GroovyGenerateToStringTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val fixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testTemplatesInManager() {
    val templates = GroovyToStringTemplatesManager.getInstance().allTemplates
    assertEquals(
      setOf("Groovy: String concat (+)", "Groovy: String concat (+) and super.toString()"),
      templates.map { it.fileName }.toSet(),
    )
  }

  @Test
  fun testStringConcat() {
    doTest("fields", "Groovy: String concat (+)")
  }

  @Test
  fun testStringConcatWithSuper() {
    doTest("superWithFields", "Groovy: String concat (+) and super.toString()")
  }

  private fun doTest(testName: String, templateName: String) {
    fixture.testDataPath = TestUtils.getAbsoluteTestDataPath() + "generate/toString"
    fixture.configureByFile("$testName.groovy")
    val template = requireNotNull(GroovyToStringTemplatesManager.getInstance().findTemplateByName(templateName))
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      val element = fixture.file.findElementAt(fixture.editor.caretModel.offset)
      val clazz = requireNotNull(PsiTreeUtil.getParentOfType(element, PsiClass::class.java))
      GenerateToStringWorker(clazz, fixture.editor, true).execute(clazz.fields.toList(), template, ReplacePolicy.getInstance())
    }
    fixture.checkResultByFile("$testName.after.groovy", true)
  }
}