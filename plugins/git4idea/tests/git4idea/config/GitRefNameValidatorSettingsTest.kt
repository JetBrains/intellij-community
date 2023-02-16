// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import git4idea.validators.GitRefNameValidator
import junit.framework.TestCase.assertEquals
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

open class GitRefNameValidatorSettingsTest {
  companion object {
    val myFakeSettings = FakeGitRefNameValidatorSettings()
    val applicationSettings: MockedStatic<GitRefNameValidatorSettings> = Mockito.mockStatic(GitRefNameValidatorSettings::class.java)

    /**
     * Only one instance of <code>GitRefNameValidator</code> is created after startup.
     * This also means there is only one instance of <code>GitRefNameValidatorSettings</code>.
     * All changes for a valid test will need to be made to that version of the settings instead of starting with a new instance.
     */
    @BeforeClass
    @JvmStatic
    fun setupStaticMock() {
      applicationSettings.`when`<Any> { GitRefNameValidatorSettings.getInstance() }
        .thenReturn(myFakeSettings)
    }

    @AfterClass
    @JvmStatic
    fun teardownStaticMock() {
      applicationSettings.close()
    }
  }

  @Before
  fun resetApplicationSettings() {
    myFakeSettings.resetSettings()
  }

  @Test
  fun `test branchName is transformed by cleanUpBranchName when isOn is true`() {
    myFakeSettings.isOn = true
    assertEquals("branchname---", GitRefNameValidator.getInstance().cleanUpBranchName("branchName..**@{"))
  }

  @Test
  fun `test branchName is transformed by cleanUpBranchNameOnTyping when isOn is true`() {
    myFakeSettings.isOn = true
    assertEquals("branchname---", GitRefNameValidator.getInstance().cleanUpBranchNameOnTyping("branchName..**@{"))
  }

  @Test
  fun `test branchName is not transformed by cleanUpBranchName when isOn is false`() {
    myFakeSettings.isOn = false
    assertEquals("branchName..**@{", GitRefNameValidator.getInstance().cleanUpBranchName("branchName..**@{"))
  }

  @Test
  fun `test branchName is not transformed by cleanUpBranchNameOnTyping when isOn is false`() {
    myFakeSettings.isOn = false
    assertEquals("branchName..**@{", GitRefNameValidator.getInstance().cleanUpBranchNameOnTyping("branchName..**@{"))
  }

  @Test
  fun `test branchName is converted by cleanUpBranchName to lowercase when isConvertingToLowerCase is true`() {
    myFakeSettings.isConvertingToLowerCase = true
    assertEquals("branchname-test", GitRefNameValidator.getInstance().cleanUpBranchName("BRanchNAME-tESt"))
  }

  @Test
  fun `test branchName is converted by cleanUpBranchNameOnTyping to lowercase when isConvertingToLowerCase is true`() {
    myFakeSettings.isConvertingToLowerCase = true
    assertEquals("branchname-test", GitRefNameValidator.getInstance().cleanUpBranchNameOnTyping("BRanchNAME-tESt"))
  }

  @Test
  fun `test branchName is not converted to lowercase by cleanUpBranchName when isConvertingToLowerCase is false`() {
    myFakeSettings.isConvertingToLowerCase = false
    assertEquals("BRanchNAME-tESt", GitRefNameValidator.getInstance().cleanUpBranchName("BRanchNAME-tESt"))
  }

  @Test
  fun `test branchName is not converted to lowercase by cleanUpBranchNameOnTyping when isConvertingToLowerCase is false`() {
    myFakeSettings.isConvertingToLowerCase = false
    assertEquals("BRanchNAME-tESt", GitRefNameValidator.getInstance().cleanUpBranchNameOnTyping("BRanchNAME-tESt"))
  }

  @Test
  fun `test for 2 maximum consecutive underscores in branch name`() {
    myFakeSettings.maxNumberOfConsecutiveUnderscores = 2
    assertEquals("branch__name", GitRefNameValidator.getInstance().cleanUpBranchName("branch____name"))
  }

  @Test
  fun `test for 3 maximum consecutive underscores in branch name`() {
    myFakeSettings.maxNumberOfConsecutiveUnderscores = 3
    assertEquals("branch___name", GitRefNameValidator.getInstance().cleanUpBranchName("branch____name"))
  }

  @Test
  fun `test replacement of invalid entries with hyphens`() {
    myFakeSettings.replacementOption = GitRefNameValidatorReplacementOption.HYPHEN
    assertEquals("branch-name", GitRefNameValidator.getInstance().cleanUpBranchName("branch@{name"))
  }

  @Test
  fun `test replacement of invalid entries with underscores`() {
    myFakeSettings.replacementOption = GitRefNameValidatorReplacementOption.UNDERSCORE
    assertEquals("branch_name", GitRefNameValidator.getInstance().cleanUpBranchName("branch@{name"))
  }
}