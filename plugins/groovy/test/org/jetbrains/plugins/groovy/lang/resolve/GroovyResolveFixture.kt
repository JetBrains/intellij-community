// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * The fixture of the Groovy resolve tests. Create it with [groovyResolveFixture].
 *
 * A member configures a file with the `<ref>` marker or the caret, and finds the reference at that offset.
 * The members read the PSI in a read action. A test that reads the PSI of a returned reference needs its own read action.
 */
@TestOnly
open class GroovyResolveFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  internal fun setUp() {
    if (File(fixture.testDataPath + "/" + getTestName(true)).exists()) {
      fixture.copyDirectoryToProject(getTestName(true), "")
    }
  }

  /**
   * Configures the project file [filePath] without its `<ref>` marker, and returns the reference at the marker.
   * When [newFilePath] is not null, the fixture configures a file of this name.
   */
  fun <T : PsiReference> configureByFile(@NonNls filePath: String, newFilePath: String?, refType: Class<T>): T {
    val trimmedFilePath = filePath.removePrefix(getTestName(true) + "/")
    val vFile = fixture.tempDirFixture.getFile(filePath) ?: fixture.tempDirFixture.getFile(trimmedFilePath)
    assertNotNull(vFile, "file $filePath not found")

    var fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile!!))
    val offset = fileText.indexOf(MARKER)
    assertTrue(offset >= 0, "'ref' marker is not found")
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length)

    fixture.configureByText(newFilePath ?: vFile.name, fileText)
    return assertInstanceOf(refType, readReferenceAt(offset))
  }

  fun configureByFile(@NonNls filePath: String, newFilePath: String?): PsiReference =
    configureByFile(filePath, newFilePath, PsiReference::class.java)

  fun configureByFile(@NonNls filePath: String): PsiReference = configureByFile(filePath, null, PsiReference::class.java)

  /**
   * Configures [fileName] with [text], and returns the reference at the caret.
   */
  fun <T : PsiReference> configureByText(fileName: String, text: String, refType: Class<T>): T {
    fixture.configureByText(fileName, text)
    val ref = ReadAction.computeBlocking<PsiReference?, RuntimeException> { fixture.file.findReferenceAt(fixture.caretOffset) }
    return assertInstanceOf(refType, ref)
  }

  fun configureByText(fileName: String, text: String): PsiReference = configureByText(fileName, text, PsiReference::class.java)

  fun configureByText(text: String): PsiReference = configureByText("_a.groovy", text, PsiReference::class.java)

  /**
   * Configures `<test name>/<fileName>` of the project and resolves the reference at the `<ref>` marker.
   * When [type] is not null, the result must be an instance of it.
   */
  fun <T : PsiElement> resolve(fileName: String, type: Class<T>?): PsiElement? {
    val ref = configureByFile(getTestName(true) + "/" + fileName)
    val resolved = ReadAction.computeBlocking<PsiElement?, RuntimeException> { ref.resolve() }
    if (type != null) assertInstanceOf(type, resolved)
    return resolved
  }

  fun resolve(fileName: String): PsiElement? = resolve<PsiElement>(fileName, null)

  fun resolve(): PsiElement? = resolve<PsiElement>(getTestName(false) + ".groovy", null)

  /**
   * Configures `_a.groovy` with [text] and resolves the reference at the caret.
   * When [type] is null, the reference must not resolve.
   */
  fun <T : PsiElement> resolveByText(text: String, type: Class<T>?): T? {
    val ref = configureByText(text)
    val resolved = ReadAction.computeBlocking<PsiElement?, RuntimeException> { ref.resolve() }
    if (type == null) {
      assertNull(resolved)
      return null
    }
    return assertInstanceOf(type, resolved)
  }

  fun resolveByText(text: String): PsiElement? = resolveByText(text, PsiElement::class.java)

  /**
   * Configures `<test name>/<fileName>` of the project and resolves the reference expression at the `<ref>` marker.
   */
  fun advancedResolve(fileName: String): GroovyResolveResult {
    val ref = assertInstanceOf(GrReferenceExpression::class.java, configureByFile(getTestName(true) + "/" + fileName))
    return ReadAction.computeBlocking<GroovyResolveResult, RuntimeException> { ref.advancedResolve() }
  }

  private fun readReferenceAt(offset: Int): PsiReference? =
    ReadAction.computeBlocking<PsiReference?, RuntimeException> { fixture.file.findReferenceAt(offset) }

  companion object {
    /**
     * The marker of the reference in a test data file.
     */
    const val MARKER: String = "<ref>"
  }
}

/**
 * Creates the [GroovyResolveFixture] of a resolve test. Declare it as an instance field.
 *
 * When [testDataPath] is not null, the fixture uses it as the absolute test data path.
 * Before the test, the fixture copies the test data directory `<test name>` to the project, when it exists.
 */
@TestOnly
@JvmOverloads
fun groovyResolveFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<GroovyResolveFixture> = testFixture("groovyResolveFixture") {
  val fixture = lightGroovyFixture(projectFixture, testDataPath).init()
  val resolve = GroovyResolveFixture(fixture)
  resolve.setUp()
  initialized(resolve) {}
}
