// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.validators

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private val ILLEGAL_CHARS = listOf(" ", "~", "^", ":", "?", "*", "[", "@{", "\\")
private val CONTROL_CHARS = (0..31).map { it.toChar().toString() } + "\u007F" // + DEL

/**
 * The test for [GitRefNameValidator].
 * Tests are based on the [specification of valid Git references](http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html)
 */
class GitRefNameValidatorTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  fun testAll(@Suppress("unused") name: String, refNameToTest: String, isExpectedValid: Boolean) {
    if (isExpectedValid) {
      assertValid(refNameToTest)
    }
    else {
      assertInvalid(refNameToTest)
    }
  }

  companion object {
    private fun createValidData(): Array<Array<String>> = arrayOf(
      arrayOf("WORD", "branch"),
      arrayOf("UNDERSCORED_WORD", "new_branch"),
      arrayOf("HIERARCHY", "user/branch"),
      arrayOf("HIERARCHY_2", "user/branch/sub_branch"),
      arrayOf("NON_CONS_DOTS", "complex.branch.name"),
      arrayOf("GERRIT_PATTERN", "refs/for/master%topic=my-cool-feature,r=some-reviewer"),
      arrayOf("CONTAINS_MINUS", "b-ranch"),
      arrayOf("STARTS_WITH_PLUS", "+branch"),
    )

    private fun createInvalidData(): Array<Array<String>> = arrayOf(
      arrayOf("BEGIN_WITH_DOT", ".branch"),
      arrayOf("BEGINS_WITH_SLASH", "/branch"),
      arrayOf("ONLY_DOT", "."),
      arrayOf("ENDS_WITH_SLASH", "branch/"),
      arrayOf("ENDS_WITH_DOT", "branch."),
      arrayOf("ENDS_WITH_LOCK", "branch.lock"),
      arrayOf("TWO_DOTS_1", "branch..name"),
      arrayOf("TWO_DOTS_2", "..name"),
      arrayOf("TWO_DOTS_3", "..branch"),
      arrayOf("EMPTY", ""),
      arrayOf("SPACES", "  "),
      arrayOf("STARTS_WITH_MINUS", "-branch"),
    )

    private fun createInvalidCharsData(): Array<Array<String>> {
      return populateWithIllegalChars(ILLEGAL_CHARS) { it }
    }

    private fun createInvalidControlCharsData(): Array<Array<String>> {
      return populateWithIllegalChars(CONTROL_CHARS) { "\\u00" + Integer.toHexString(it[0].code) }
    }

    private fun populateWithIllegalChars(illegalChars: List<String>, toString: (String) -> String): Array<Array<String>> {
      return Array(illegalChars.size) { i ->
        arrayOf(toString(illegalChars[i]), "bra" + illegalChars[i] + "nch")
      }
    }

    @JvmStatic
    fun data(): Collection<Array<Any>> = buildList {
      populateData(createValidData(), true)
      populateData(createInvalidData(), false)
      populateData(createInvalidCharsData(), false)
      populateData(createInvalidControlCharsData(), false)
    }

    private fun MutableList<Array<Any>>.populateData(source: Array<Array<String>>, valid: Boolean) {
      source.forEach { testCase -> add(arrayOf(testCase[0], testCase[1], valid)) }
    }

    private fun assertValid(branchName: String) {
      val validator = GitRefNameValidator.getInstance()
      assertTrue(validator.checkInput(branchName), "Should be valid")
      assertTrue(validator.canClose(branchName), "Should be valid")
    }

    private fun assertInvalid(branchName: String) {
      val validator = GitRefNameValidator.getInstance()
      assertFalse(validator.checkInput(branchName), "Should be invalid")
      assertFalse(validator.canClose(branchName), "Should be invalid")
    }
  }
}