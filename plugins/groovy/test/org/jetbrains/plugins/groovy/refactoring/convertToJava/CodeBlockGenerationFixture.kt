// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.junit.jupiter.api.Assertions.assertInstanceOf

/**
 * Checks the Java code that [CodeBlockGenerator] makes from Groovy code. It replaces the JUnit 3 base `CodeBlockGenerationBaseTest`.
 * The helpers work on the EDT and off it.
 */
@TestOnly
class CodeBlockGenerationFixture internal constructor(val fixture: LightJavaCodeInsightTestFixture) {
  private val testName: String
    get() = fixture.getTestName(false)

  /** Converts the file `<test name>.groovy` and compares the result with the file `<test name>.java`. */
  fun doTest() {
    val name = fixture.getTestName(true)
    val file = assertInstanceOf(GroovyFile::class.java, fixture.configureByFile("$name.groovy"))

    val code = ReadAction.computeBlocking<String, Throwable> {
      val builder = StringBuilder()
      val generator = CodeBlockGenerator(builder, ExpressionContext(fixture.project, GroovyFile.EMPTY_ARRAY))
      for (statement in file.topStatements) {
        statement.accept(generator)
        builder.append("\n")
      }
      builder.toString()
    }

    val resultText = timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val result = PsiFileFactory.getInstance(fixture.project).createFileFromText("$name.java", JavaLanguage.INSTANCE, code, false, true)
        PostprocessReformattingAspect.getInstance(fixture.project).doPostponedFormatting()
        result.text
      }
    }
    PlatformTestUtil.assertSameLinesWithFile(Path.of(fixture.testDataPath, "$name.java").toString(), resultText)
  }

  fun addFile(text: String): PsiFile = fixture.addFileToProject("Bar.groovy", text)
}

/**
 * Creates a [CodeBlockGenerationFixture] in the project of [projectFixture] for each test.
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 */
@TestOnly
@JvmOverloads
fun codeBlockGenerationFixture(projectFixture: TestFixture<LightJavaProject>, testDataPath: String? = null): TestFixture<CodeBlockGenerationFixture> =
  testFixture("codeBlockGenerationFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    if (testDataPath != null) {
      fixture.testDataPath = testDataPath
    }
    initialized(CodeBlockGenerationFixture(fixture)) {}
  }
