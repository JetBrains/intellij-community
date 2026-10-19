// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.mock.MockLanguageFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ParsingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Parser test helpers for a JUnit 5 test. Create the fixture with [parsingFixture].
 * This is the JUnit 5 replacement of [com.intellij.testFramework.ParsingTestCase].
 *
 * The fixture parses the files in the project of the project fixture, with the parser definitions of the test application.
 * Each function that reads PSI runs in a read action, and each function that changes PSI runs in a write action.
 * A caller can call them from any thread, but not in a read action.
 *
 * The parsed files are physical. A PSI change in the test body, outside these functions, needs a write action,
 * for example `WriteAction.runAndWait`. `ParsingTestCase` did not check the write access.
 */
@TestOnly
class ParsingTestFixture internal constructor(
  val project: Project,
  /**
   * The directory of the test data files, as `ParsingTestCase.myFullDataPath`.
   */
  val fullDataPath: String,
  /**
   * The name of the current test method without the `test` prefix, as `ParsingTestCase.getTestName()`.
   * Set it in `@BeforeEach` when the test data files use other names.
   */
  var testName: String,
  /**
   * The extension of the test data files and of the parsed files, without a dot.
   */
  var fileExt: String,
  language: Language?,
  /**
   * The disposable of the current test. The fixture disposes it after the test.
   */
  val testRootDisposable: Disposable,
) {
  /**
   * The language of the parsed files. [configureFromParserDefinition] sets it.
   *
   * The first parser definition can have the file node type of another language, for example `XHTMLParserDefinition`.
   * Then the parse fails with "View provider ... refused to parse text". Set the language in `@BeforeEach`.
   */
  var language: Language
    get() = checkNotNull(myLanguage) { "The language is not set. Call configureFromParserDefinition." }
    set(value) {
      myLanguage = value
    }

  private var myLanguage: Language? = language

  /**
   * The prefix of the expected file name in [doCodeTest] and [checkResult], as `ParsingTestCase.myFilePrefix`.
   */
  var filePrefix: String = ""

  /**
   * When `true`, the PSI tree text has no whitespace elements, as `ParsingTestCase.skipSpaces()`.
   */
  var skipSpaces: Boolean = false

  /**
   * When `true`, the PSI tree text has the text ranges, as `ParsingTestCase.includeRanges()`.
   */
  var includeRanges: Boolean = false

  /**
   * When `true`, the checks and the expected files cover each PSI root of a file, as `ParsingTestCase.checkAllPsiRoots()`.
   */
  var checkAllPsiRoots: Boolean = true

  /**
   * When `true`, one expected file holds the trees of all PSI roots, as `ParsingTestCase.allTreesInSingleFile()`.
   */
  var allTreesInSingleFile: Boolean = false

  /**
   * When `false`, the reparse check accepts PSI events, as an override of `ParsingTestCase.isCheckNoPsiEventsOnReparse()`.
   * Do not set it in new code. Fix the parser instead.
   */
  var checkNoPsiEventsOnReparse: Boolean = true

  /**
   * The file of the last [doTest], [parseFile], [doCodeTest], or `doTest(suffix)` call.
   */
  var file: PsiFile? = null

  /**
   * Sets [language] and [fileExt] from [definition], and registers [definition] for the current test.
   */
  fun configureFromParserDefinition(definition: ParserDefinition, extension: String) {
    language = definition.fileNodeType.language
    fileExt = extension
    registerParserDefinition(definition)
  }

  /**
   * Registers [definition] for its language for the current test.
   * The fixture does nothing when the application already uses a definition of the same class.
   */
  fun registerParserDefinition(definition: ParserDefinition) {
    val language = definition.fileNodeType.language
    if (LanguageParserDefinitions.INSTANCE.forLanguage(language)?.javaClass != definition.javaClass) {
      LanguageParserDefinitions.INSTANCE.addExplicitExtension(language, definition, testRootDisposable)
    }
  }

  /**
   * Adds [extension] to [collector] for [language] for the current test.
   */
  fun <T : Any> addExplicitExtension(collector: LanguageExtension<T>, language: Language, extension: T) {
    collector.addExplicitExtension(language, extension, testRootDisposable)
  }

  /**
   * Loads `<testName>.<fileExt>`, parses it, and runs the checks of [parseFile].
   * When [checkResult] is `true`, compares the PSI tree with the expected file `<testName>.txt`.
   * When [ensureNoErrorElements] is also `true`, checks that the tree has no error elements.
   */
  @JvmOverloads
  fun doTest(checkResult: Boolean, ensureNoErrorElements: Boolean = false) {
    val name = testName
    val text = loadFile("$name.$fileExt")
    val file = parseFile(name, text)
    if (checkResult) {
      this.checkResult(name, file)
      if (ensureNoErrorElements) {
        ensureNoErrorElements()
      }
    }
    else {
      ReadAction.computeBlocking<String, RuntimeException> { toParseTreeText(file, skipSpaces, includeRanges) }
    }
  }

  /**
   * Loads `<testName>.<fileExt>`, parses it, and compares the PSI tree with the expected file `<testName><suffix>.txt`.
   */
  fun doTest(suffix: String) {
    val name = testName
    val text = loadFile("$name.$fileExt")
    val file = createParsedFile(name, text)
    checkResult(name + suffix, file)
  }

  /**
   * Parses [code] and compares the PSI tree with the expected file `<filePrefix><testName>.txt`.
   */
  fun doCodeTest(code: String) {
    val file = createParsedFile("a", code)
    checkResult(filePrefix + testName, file)
  }

  private fun createParsedFile(name: String, text: String): PsiFile {
    val file = createPsiFile(name, text)
    this.file = file
    ReadAction.computeBlocking<Unit, RuntimeException> {
      ParsingTestUtil.ensureParsed(file)
      assertEquals(text, file.text)
    }
    return file
  }

  /**
   * Checks that the PSI tree of [file] has no error elements.
   */
  @JvmOverloads
  fun ensureNoErrorElements(file: PsiFile = checkNotNull(this.file)) {
    ReadAction.computeBlocking<Unit, RuntimeException> { assertNoPsiErrorElements(file) }
  }

  /**
   * Creates the file `<name>.<fileExt>` with [text], and runs the checks of `ParsingTestCase.parseFile`.
   * The checks compare the texts, parse the full tree, reparse each PSI root, and check the node ranges.
   */
  fun parseFile(name: String, text: String): PsiFile = checkParsedFile(createPsiFile(name, text), text)

  /**
   * Runs the checks of [parseFile] on [file], and stores it in [ParsingTestFixture.file].
   * [file] has the text [text]. Use this function for a file that the test creates itself.
   * When [checkNoPsiEventsOnReparse] is `false`, the reparse runs in a write action, so do not call this function in a read action.
   */
  fun checkParsedFile(file: PsiFile, text: String): PsiFile {
    this.file = file
    val roots = ReadAction.computeBlocking<List<PsiFile>, RuntimeException> {
      assertEquals(text, (file.virtualFile as LightVirtualFile).content.toString(), "light virtual file text mismatch")
      assertEquals(text, LoadTextUtil.loadText(file.virtualFile).toString(), "virtual file text mismatch")
      assertEquals(text, checkNotNull(file.viewProvider.document).text, "doc text mismatch")
      if (checkAllPsiRoots) file.viewProvider.allFiles else listOf(file)
    }
    for (root in roots) {
      doSanityChecks(root)
    }
    return file
  }

  private fun doSanityChecks(root: PsiFile) {
    ReadAction.computeBlocking<Unit, RuntimeException> {
      assertEquals(root.viewProvider.contents.toString(), root.text, "psi text mismatch")
      ParsingTestUtil.ensureParsed(root)
    }
    if (checkNoPsiEventsOnReparse) {
      ReadAction.computeBlocking<Unit, RuntimeException> { ensureCorrectReparse(root, true) }
    }
    else {
      // The reparse can change the tree of a physical file, and a PSI change needs a write action.
      WriteAction.runAndWait<RuntimeException> { ensureCorrectReparse(root, false) }
    }
    ReadAction.computeBlocking<Unit, RuntimeException> { checkRangeConsistency(root) }
  }

  /**
   * Parses [textBefore], reparses the changed range with [textAfter], and compares the tree with a full parse of [textAfter].
   * The reparse runs in a write action, so do not call this function in a read action.
   */
  fun doReparseTest(textBefore: String, textAfter: String) {
    val file = createFile("test.$fileExt", textBefore)
    val fileAfter = createFile("test.$fileExt", textAfter)

    val rangeStart = StringUtil.commonPrefixLength(textBefore, textAfter)
    val rangeEnd = textBefore.length - StringUtil.commonSuffixLength(textBefore, textAfter)
    val range = TextRange(minOf(rangeStart, rangeEnd), maxOf(rangeStart, rangeEnd))

    val psiToStringDefault = ReadAction.computeBlocking<String, RuntimeException> {
      DebugUtil.psiToString(fileAfter, true, false, true, null)
    }
    // The reparse changes the tree of a physical file, and a PSI change needs a write action.
    WriteAction.runAndWait<RuntimeException> {
      DebugUtil.performPsiModification<RuntimeException>("ensureCorrectReparse") {
        BlockSupportImpl()
          .reparseRange(file, file.node, range, fileAfter.text, EmptyProgressIndicator(), file.text)
          .performActualPsiChange(file)
      }
    }
    ReadAction.computeBlocking<Unit, RuntimeException> {
      assertEquals(psiToStringDefault, DebugUtil.psiToString(file, true, false, true, null))
    }
  }

  /**
   * Creates the file `<name>.<fileExt>` with [text].
   */
  fun createPsiFile(name: String, text: String): PsiFile = createFile("$name.$fileExt", text)

  /**
   * Creates the file [name] of [language] with [text], as `ParsingTestCase.createFile` did.
   * The file is a light, physical file with the UTF-8 charset.
   * When the application knows no file type for the language and the name, the file gets a mock file type of the language,
   * as the mock file type manager of `ParsingTestCase` gave.
   */
  fun createFile(name: String, text: String): PsiFile {
    val virtualFile = LightVirtualFile(name, language, text)
    virtualFile.charset = StandardCharsets.UTF_8
    if (virtualFile.fileType.isBinary) {
      virtualFile.fileType = MockLanguageFileType(language, fileExt)
    }
    return createFile(virtualFile)
  }

  /**
   * Creates the PSI file of [virtualFile] for [language].
   */
  fun createFile(virtualFile: LightVirtualFile): PsiFile {
    return ReadAction.computeBlocking<PsiFile, RuntimeException> {
      val factory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
      checkNotNull(factory.trySetupPsiForFile(virtualFile, language, true, false)) {
        "No PSI file for ${virtualFile.name} and the language ${language.id}"
      }
    }
  }

  /**
   * Compares the PSI tree of [file] with the expected file `<targetDataName>.txt` in [fullDataPath].
   */
  fun checkResult(@TestDataFile targetDataName: String, file: PsiFile) {
    checkResult(fullDataPath, targetDataName, file)
  }

  /**
   * Compares the PSI tree of [file] with the expected file `<targetDataName>.txt` in [fullDataPath].
   * See [doCheckResult] for a file with several PSI roots.
   */
  fun checkResult(fullDataPath: String, @TestDataFile targetDataName: String, file: PsiFile) {
    ReadAction.computeBlocking<Unit, RuntimeException> {
      doCheckResult(fullDataPath, file, checkAllPsiRoots, targetDataName, skipSpaces, includeRanges, allTreesInSingleFile)
      if (SystemProperties.getBooleanProperty("dumpAstTypeNames", false)) {
        printAstTypeNamesTree(targetDataName, file)
      }
    }
  }

  /**
   * Compares [actual] with the expected file `<filePrefix><testName>.txt` in [fullDataPath].
   */
  fun checkResult(actual: String) {
    doCheckResult(fullDataPath, "$filePrefix$testName.txt", actual)
  }

  /**
   * Compares [actual] with the expected file [targetDataName] in [fullDataPath]. [targetDataName] has the extension.
   */
  fun checkResult(@TestDataFile targetDataName: String, actual: String) {
    doCheckResult(fullDataPath, targetDataName, actual)
  }

  /**
   * Returns the trimmed text of the file [name] in [fullDataPath]. [name] has the extension.
   */
  fun loadFile(@TestDataFile name: String): String = loadFileDefault(fullDataPath, name)

  private fun printAstTypeNamesTree(targetDataName: String, file: PsiFile) {
    val buffer = StringBuilder()
    for (child in file.node.getChildren(TokenSet.ANY)) {
      printAstTypeNamesTree(child, buffer, 0)
    }
    try {
      Files.writeString(Paths.get(fullDataPath, "$targetDataName.fleet.txt"), buffer)
    }
    catch (e: IOException) {
      e.printStackTrace()
    }
  }

  @TestOnly
  companion object {
    /**
     * Compares the PSI tree of [file] with the expected file `<targetDataName>.txt` in [testDataDir], as `ParsingTestCase.doCheckResult`.
     * When [checkAllPsiRoots] is `true` and the file has several PSI roots, each root has the expected file
     * `<targetDataName>.<language id>.txt`. When [allTreesInSingleFile] is also `true`, one expected file holds all trees.
     * Call it in a read action.
     */
    @JvmStatic
    @JvmOverloads
    fun doCheckResult(
      testDataDir: String,
      file: PsiFile,
      checkAllPsiRoots: Boolean,
      targetDataName: String,
      skipSpaces: Boolean,
      printRanges: Boolean,
      allTreesInSingleFile: Boolean = false,
    ) {
      val provider = file.viewProvider
      val languages = provider.languages
      if (!checkAllPsiRoots || languages.size == 1) {
        doCheckResult(testDataDir, "$targetDataName.txt", toParseTreeText(file, skipSpaces, printRanges).trim { it <= ' ' })
        return
      }
      if (allTreesInSingleFile) {
        val sb = StringBuilder()
        for (language in languages.sortedBy { it.id }) {
          sb.append("Subtree: ").append(language.displayName).append(" (").append(language.id).append(")").append("\n")
            .append(toParseTreeText(provider.getPsi(language)!!, skipSpaces, printRanges).trim { it <= ' ' })
            .append("\n").append(StringUtil.repeat("-", 80)).append("\n")
        }
        doCheckResult(testDataDir, "$targetDataName.txt", sb.toString())
      }
      else {
        for (language in languages) {
          val root = provider.getPsi(language)
          assertNotNull(root, "FileViewProvider $provider didn't return PSI root for language ${language.id}")
          doCheckResult(testDataDir, "$targetDataName.${language.id}.txt", toParseTreeText(root!!, skipSpaces, printRanges).trim { it <= ' ' })
        }
      }
    }

    /**
     * Compares [actual] with the file [targetDataName] in [fullPath], as `ParsingTestCase.doCheckResult`.
     */
    @JvmStatic
    fun doCheckResult(fullPath: String, targetDataName: String, actual: String) {
      PlatformTestUtil.assertSameLinesWithFile(Paths.get(fullPath, targetDataName).toString(), actual)
    }

    /**
     * Returns the PSI tree text of [element]. Call it in a read action.
     */
    @JvmStatic
    fun toParseTreeText(element: PsiElement, skipSpaces: Boolean, printRanges: Boolean): String {
      return DebugUtil.psiToString(element, !skipSpaces, printRanges)
    }

    /**
     * Returns the trimmed text of the file [name] in [dir], as `ParsingTestCase.loadFileDefault`.
     */
    @JvmStatic
    fun loadFileDefault(dir: String, name: String): String {
      return StringUtil.convertLineSeparators(String(Files.readAllBytes(Paths.get(dir, name)), StandardCharsets.UTF_8)).trim { it <= ' ' }
    }

    private fun ensureCorrectReparse(file: PsiFile, checkNoPsiEvents: Boolean) {
      val psiToStringDefault = DebugUtil.psiToString(file, true, false)
      val event = InternalPsiVersioning.runModificationOfVersionedPsi {
        DebugUtil.performPsiModification<TreeChangeEventImpl, RuntimeException>("ensureCorrectReparse") {
          val fileText = file.text
          BlockSupportImpl()
            .reparseRange(file, file.node, TextRange.allOf(fileText), fileText, EmptyProgressIndicator(), fileText)
            .performActualPsiChange(file)
        }
      }
      assertEquals(psiToStringDefault, DebugUtil.psiToString(file, true, false))
      if (checkNoPsiEvents) {
        assertTrue(event.changedElements.isEmpty(), "The reparse changed the elements: ${event.changedElements}")
      }
    }

    private fun assertNoPsiErrorElements(file: PsiFile) {
      val errors = ArrayList<String>()
      file.accept(object : PsiRecursiveElementVisitor() {
        override fun visitErrorElement(element: PsiErrorElement) {
          errors.add("${element.textOffset}: ${element.errorDescription}")
          super.visitErrorElement(element)
        }
      })
      if (errors.isNotEmpty()) {
        fail<Unit>("Found PsiElement errors at offsets:\n" + errors.joinToString("\n"))
      }
    }

    private fun checkRangeConsistency(file: PsiFile) {
      file.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
          if (element is ForeignLeafPsiElement) return
          try {
            ensureNodeRangeConsistency(element, file)
          }
          catch (e: Throwable) {
            throw AssertionError("In $element of ${element.javaClass}", e)
          }
          super.visitElement(element)
        }
      })
    }

    private fun ensureNodeRangeConsistency(parent: PsiElement, file: PsiFile) {
      val parentOffset = parent.textRange.startOffset
      var childOffset = 0
      var child = parent.node.firstChildNode ?: return
      while (true) {
        childOffset += checkChildRangeConsistency(file, parentOffset, childOffset, child)
        child = child.treeNext ?: break
      }
      assertEquals(childOffset, parent.textLength)
    }

    private fun checkChildRangeConsistency(file: PsiFile, parentOffset: Int, childOffset: Int, child: ASTNode): Int {
      assertEquals(child.startOffsetInParent, childOffset)
      assertEquals(child.startOffset, childOffset + parentOffset)
      val childLength = child.textLength
      assertEquals(TextRange.from(childOffset + parentOffset, childLength), child.textRange)
      if (child.psi !is ForeignLeafPsiElement) {
        assertEquals(child.textRange.substring(file.text), child.text)
      }
      return childLength
    }

    private fun printAstTypeNamesTree(node: ASTNode, buffer: StringBuilder, indent: Int) {
      buffer.append(" ".repeat(indent)).append(node.elementType).append("\n")
      var child = node.firstChildNode
      while (child != null) {
        printAstTypeNamesTree(child, buffer, indent + 2)
        child = child.treeNext
      }
    }
  }
}

/**
 * Creates a [ParsingTestFixture] that parses the files in the project of [projectFixture].
 *
 * [fullDataPath] is the directory of the test data files, as `ParsingTestCase.myFullDataPath`.
 * [fileExt] is the extension of the files, without a dot.
 * When [lowercaseFirstLetter] is `true`, [ParsingTestFixture.testName] starts with a lowercase letter.
 * The language of the files is the language of the first item of [definitions].
 * The fixture registers each item of [definitions] for the test, as `ParsingTestCase` did.
 * Without [definitions], call [ParsingTestFixture.configureFromParserDefinition] before a parse.
 *
 * The fixture takes the name of the current test method, so declare it as an instance property.
 * Declare [projectFixture] in a companion object or as a static field, and annotate the test class with
 * [com.intellij.testFramework.junit5.TestApplication].
 */
@TestOnly
@JvmOverloads
fun parsingFixture(
  projectFixture: TestFixture<Project>,
  fullDataPath: String,
  fileExt: String,
  lowercaseFirstLetter: Boolean = false,
  vararg definitions: ParserDefinition,
): TestFixture<ParsingTestFixture> = testFixture("parsingFixture") { context ->
  val testMethod = context.extensionContext.testMethod.orElseThrow {
    IllegalStateException("parsingFixture needs a test method. Declare it as an instance property.")
  }
  val disposable = Disposer.newDisposable("parsingFixture")
  val fixture = ParsingTestFixture(
    project = projectFixture.init(),
    fullDataPath = fullDataPath,
    testName = PlatformTestUtil.getTestName(testMethod.name, lowercaseFirstLetter),
    fileExt = fileExt,
    language = definitions.firstOrNull()?.fileNodeType?.language,
    testRootDisposable = disposable,
  )
  for (definition in definitions) {
    fixture.registerParserDefinition(definition)
  }
  initialized(fixture) {
    Disposer.dispose(disposable)
  }
}
