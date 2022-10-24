package org.jetbrains.completion.full.line.platform.tests

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.completion.full.line.FullLineProposal.BasicSyntaxCorrectness
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.ReferenceCorrectness
import org.jetbrains.completion.full.line.language.RedCodePolicy
import org.jetbrains.completion.full.line.platform.FullLineContributor
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.providers.FullLineCompletionProvider
import org.jetbrains.completion.full.line.services.TestFullLineCompletionProvider
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable

@Suppress("MemberVisibilityCanBePrivate")
abstract class FullLineCompletionTestCase(private val mockCompletionProvider: Boolean = true) : BasePlatformTestCase() {
  // override `getBasePath` for better navigation in resources, in IDE
  override fun getTestDataPath() = PluginPathManager.getPluginHome("full-line").resolve(basePath).path

  override fun setUp() {
    super.setUp()
    if (mockCompletionProvider) FullLineCompletionProvider.mockProvider(TestFullLineCompletionProvider(), testRootDisposable)
    MLServerCompletionSettings.availableLanguages.forEach {
      MLServerCompletionSettings.getInstance().getLangState(it).enabled = true
      MLServerCompletionSettings.getInstance().getLangState(it).redCodePolicy = RedCodePolicy.DECORATE
    }
    Registry.get("full.line.multi.token.everywhere").setValue(true)
    MLServerCompletionSettings.getInstance().state.useTopN = false
    MLServerCompletionSettings.getInstance().state.enable = true
  }

  @Suppress("SSBasedInspection")
  override fun tearDown() {
    try {
      MLServerCompletionSettings.getInstance().state.useTopN = true
      MLServerCompletionSettings.getInstance().state.enable = false
      TestFullLineCompletionProvider.clear()
      Registry.get("full.line.multi.token.everywhere").setValue(false)
    }
    finally {
      super.tearDown()
    }

  }

  fun wrapJUnit3TestCase(block: () -> Unit) {
    try {
      setUp()
      block()
    }
    catch (ignored: Exception) {
      throw ignored
    }
    finally {
      tearDown()
    }
  }

  fun stepByStepByText(language: String, context: String = "", lookupShownAfter: Boolean = false, test: StepByStep.() -> Unit) {
    val type = fileTypeFromLanguage(language)
    myFixture.configureByText(type, context)

    runTest(lookupShownAfter, test)
  }

  fun stepByStepByFile(filePath: String, lookupShownAfter: Boolean = true, test: StepByStep.() -> Unit) {
    myFixture.configureByFile(filePath)

    runTest(lookupShownAfter, test)
  }

  private fun runTest(lookupShownAfter: Boolean, test: StepByStep.() -> Unit) {
    StepByStep(myFixture).test()
    assertEquals(lookupShownAfter, myFixture.lookup != null)
  }

  /**
   * Test for language will show specific variants and insert the first one by typing enter
   */
  fun doEnterTest(lang: String, vararg variants: String) = doTestWithInsertion(lang, *variants, completionChar = '\n')

  /**
   * Test for language will show specific variants and insert the first one by typing tab
   */
  fun doTabTest(lang: String, vararg variants: String) = doTestWithInsertion(lang, *variants, completionChar = '\t')

  /**
   * Test if passed variant
   */
  fun testIfSuggestionsRefCorrect(vararg suggestions: String) = doTestSuggestionsAndRef(
    *suggestions, refCorrectness = ReferenceCorrectness.CORRECT
  )

  fun testIfSuggestionsRefInCorrect(vararg suggestions: String) = doTestSuggestionsAndRef(
    *suggestions, refCorrectness = ReferenceCorrectness.INCORRECT
  )

  /**
   * Might be useful since [FullLineContributor] caches variants by offset
   * @param tempOffset - temporary offset to clear current offset cache
   */
  fun clearFLCompletionCache(tempOffset: Int = 0) {
    val originalOffset = myFixture.caretOffset
    myFixture.editor.caretModel.moveToOffset(tempOffset)
    myFixture.completeBasic()
    myFixture.editor.caretModel.moveToOffset(originalOffset)
  }

  private fun doTestSuggestionsAndRef(vararg suggestions: String, refCorrectness: ReferenceCorrectness) {
    myFixture.apply {
      completeFullLine(*suggestions)
      assertNotNull(lookupElements)

      lookupElements!!.filterIsInstance<FullLineLookupElement>()
        .also { assertEquals(suggestions.toList().size, it.size) }
        .map {
          Executable {
            assertEquals(
              refCorrectness,
              it.proposal.refCorrectness,
              "Got unexpected correctness for ${it.lookupString}. `$refCorrectness` expected."
            )
          }
        }.also { assertAll(it) }
      lookup.hideLookup(true)
    }

    TestFullLineCompletionProvider.clear()
    clearFLCompletionCache()
  }

  private fun doTestWithInsertion(lang: String, vararg variants: String, completionChar: Char) {
    val testName = getTestName(false).trim().takeLastWhile { !it.isWhitespace() }
    val fileType = fileTypeFromLanguage(lang)

    // Reload file if already in memory
    myFixture.file?.virtualFile?.let {
      FileDocumentManager.getInstance().reloadFiles(it)
    }

    myFixture.configureByFile("$testName.${fileType.defaultExtension}")
    clearFLCompletionCache()

    // Call completion
    myFixture.completeFullLine(*variantsToProposals(*variants))

    assertNotNull(myFixture.lookup)

    // Select item
    myFixture.lookup.currentItem = myFixture.lookupElements?.filterIsInstance<FullLineLookupElement>()?.first()
    myFixture.finishLookup(completionChar)

    // Asserts
    when (completionChar) {
      '\t' -> myFixture.checkResultByFile("${testName}_tab.${fileType.defaultExtension}")
      '\n' -> myFixture.checkResultByFile("${testName}_enter.${fileType.defaultExtension}")
      else -> throw IllegalArgumentException("Passed wrong completion char <$completionChar>")
    }
  }

  /**
   * Get file type from language and check if it exists
   */
  fun fileTypeFromLanguage(lang: String): FileType {
    val language = Language.findLanguageByID(lang)
    assertNotNull(language, "Passed non existed language. Supported: <${Language.getRegisteredLanguages()}>")
    return FileTypeManager.getInstance().findFileTypeByLanguage(language!!)!!
  }

  /**
   * Fix test name from file generation for files with spaces instead of camel case
   */
  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return super.getTestName(lowercaseFirstLetter).trim()
      .split(" ")
      .joinToString("") {
        it.capitalize()
      }
  }

  /**
   * Call completion with mocked completion provider
   */
  fun CodeInsightTestFixture.completeFullLine(vararg variants: String) = completeFullLine(*variantsToProposals(*variants))

  fun CodeInsightTestFixture.completeFullLine(vararg variants: RawFullLineProposal): Array<LookupElement>? {
    variants.let { TestFullLineCompletionProvider.variants.addAll(it) }

    return completeBasic()
  }

  private fun variantsToProposals(vararg variants: String) = variantsToProposals(variants.toList())

  private fun variantsToProposals(variants: List<String>) = variants.mapIndexed { i, it -> proposal(it, 1.0 - i * 0.001) }.toTypedArray()

  /**
   * Create proposal from raw data
   */
  fun proposal(suggestion: String, score: Double, isCorrect: Boolean = true): RawFullLineProposal {
    return RawFullLineProposal(suggestion, score, BasicSyntaxCorrectness.fromBoolean(isCorrect))
  }

  fun CodeInsightTestFixture.fullLineElement(): List<FullLineLookupElement> {
    return lookupElements!!.filterIsInstance<FullLineLookupElement>()
  }
}
