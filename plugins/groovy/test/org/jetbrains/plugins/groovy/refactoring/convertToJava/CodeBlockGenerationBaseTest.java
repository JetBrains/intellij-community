// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement
import org.jetbrains.plugins.groovy.util.TestUtils

import java.nio.file.Path

abstract class CodeBlockGenerationBaseTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'refactoring/convertGroovyToJava/codeBlock'

  protected void doTest() {
    final String testName = getTestName(true)
    final PsiFile file = myFixture.configureByFile(testName + '.groovy')
    assertInstanceOf file, GroovyFile

    GrTopStatement[] statements = file.topStatements
    final StringBuilder builder = new StringBuilder()
    def generator = new CodeBlockGenerator(builder, new ExpressionContext(project))
    for (def statement : statements) {
      statement.accept(generator)
      builder.append('\n')
    }

    final PsiFile result = createLightFile(testName + '.java', JavaLanguage.INSTANCE, builder.toString())
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    final String text = result.text
    assertSameLinesWithFile(Path.of(getTestDataPath(), testName + '.java').toString(), text)
  }

  protected addFile(String text) {
    myFixture.addFileToProject("Bar.groovy", text)
  }
}
