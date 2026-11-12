// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CodeBlockGenerationFixture")

package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.junit.jupiter.api.Assertions.assertInstanceOf
import java.nio.file.Path

/**
 * Checks the Java code that [CodeBlockGenerator] makes from Groovy code. It replaces the JUnit 3 base `CodeBlockGenerationBaseTest`.
 * Call the helpers on the EDT.
 */
@TestOnly
class CodeBlockGenerationTestFixture internal constructor(val fixture: JavaCodeInsightTestFixture, private val testName: String) {
  /** Converts the file `<test name>.groovy` and compares the result with the file `<test name>.java`. */
  fun doTest() {
    val name = PlatformTestUtil.lowercaseFirstLetter(testName, true)
    val file = assertInstanceOf(GroovyFile::class.java, fixture.configureByFile("$name.groovy"))

    val builder = StringBuilder()
    val generator = CodeBlockGenerator(builder, ExpressionContext(fixture.project, GroovyFile.EMPTY_ARRAY))
    for (statement in file.topStatements) {
      statement.accept(generator)
      builder.append("\n")
    }

    val result = PsiFileFactory.getInstance(fixture.project).createFileFromText("$name.java", JavaLanguage.INSTANCE, builder.toString(), false, true)
    PostprocessReformattingAspect.getInstance(fixture.project).doPostponedFormatting()
    PlatformTestUtil.assertSameLinesWithFile(Path.of(fixture.testDataPath, "$name.java").toString(), result.text)
  }

  fun addFile(text: String): PsiFile = fixture.addFileToProject("Bar.groovy", text)
}

/** Creates a [CodeBlockGenerationTestFixture] on [groovy] for each test. */
@TestOnly
fun codeBlockGenerationFixture(groovy: TestFixture<JavaCodeInsightTestFixture>): TestFixture<CodeBlockGenerationTestFixture> =
  testFixture("codeBlockGenerationFixture") { context ->
    initialized(CodeBlockGenerationTestFixture(groovy.init(), context.testName)) {}
  }
