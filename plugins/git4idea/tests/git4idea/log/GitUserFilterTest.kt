// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsLogUserFilterTest
import com.intellij.vcs.log.VcsUser
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `git log` supports multiple "--author=Author Name" arguments
 */
private val LOG_AUTHOR_FILTER_SUPPORTS_MULTIPLE_AUTHORS = GitVersion(1, 7, 4, 0)

@TestApplication
internal class GitUserFilterTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var userFilterTest: VcsLogUserFilterTest

  @BeforeEach
  fun setUp() {
    with(context) {
      userFilterTest = object : VcsLogUserFilterTest(logProvider, project) {
        override fun commit(user: VcsUser): String = makeCommit(user, "file.txt")
      }
    }
  }

  @Test
  fun `test full matching`() {
    userFilterTest.testFullMatching()
  }

  @Test
  fun `test synonyms`() {
    assumeMultipleUserFiltersWork()
    // commit by user with < or > in username does not contain them somewhy
    userFilterTest.testSynonyms(setOf('<', '>'))
  }

  @Test
  fun `test turkish locale`() {
    assumeMultipleUserFiltersWork()
    userFilterTest.testTurkishLocale()
  }

  private fun assumeMultipleUserFiltersWork() {
    val version = context.vcs.version
    assumeTrue(LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(version) ||
               version.isLaterOrEqual(LOG_AUTHOR_FILTER_SUPPORTS_MULTIPLE_AUTHORS)) {
      "Not testing: filtering by several users does not work on mac os with git prior to 1.7.4"
    }
  }

  @Test
  fun `test weird characters`() {
    userFilterTest.testWeirdCharacters()
  }

  @Test
  fun `test weird names`() {
    userFilterTest.testWeirdNames()
  }

  @Test
  fun `test jeka`() {
    userFilterTest.testJeka()
  }

  @Test
  fun `test name at surname emails`() {
    userFilterTest.testNameAtSurnameEmails()
  }
}
