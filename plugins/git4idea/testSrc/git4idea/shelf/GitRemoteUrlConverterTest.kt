// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.shelf

import com.intellij.openapi.project.Project
import git4idea.statistics.GitAvailabilityChecker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito

class GitRemoteUrlConverterTest {

  val availabilityChecker = GitAvailabilityChecker(Mockito.mock(Project::class.java))

  @ParameterizedTest
  @MethodSource("casesProvider")
  fun testConvertRemoteToHttpUrl(remote: String, expectedResult: String) {
    Assertions.assertEquals(expectedResult, availabilityChecker.getHttpUrlFromRemote (remote))
  }

  companion object {
    @JvmStatic
    fun casesProvider(): List<Arguments> = listOf(Arguments.of("http://site.com/path/project.git", "http://site.com/path/project.git"),
                                                  Arguments.of("http://site.com/path/to/repo.git", "http://site.com/path/to/repo.git"),
                                                  Arguments.of("http://login@site.com/user/project.git", "http://site.com/user/project.git"),
                                                  Arguments.of("http://login:password@site.com/user/project.git", "http://site.com/user/project.git"),
                                                  Arguments.of("https://site.com/user/project.git", "https://site.com/user/project.git"),
                                                  Arguments.of("https://site.com/path/to/repo.git", "https://site.com/path/to/repo.git"),
                                                  Arguments.of("https://login@site.com/user/project.git", "https://site.com/user/project.git"),
                                                  Arguments.of("https://login:password@site.com/user/project.git", "https://site.com/user/project.git"),
                                                  Arguments.of("login@site.com:project.git", "https://site.com/project.git"),
                                                  Arguments.of("login@site.com:path/to/repo.git", "https://site.com/path/to/repo.git"),
                                                  Arguments.of("login@site.com:/path/to/repo.git", "https://site.com/path/to/repo.git"),
                                                  Arguments.of("login:password@site.com:project.git", "https://site.com/project.git"),
                                                  Arguments.of("login:password@site.com:/path/to/repo.git", "https://site.com/path/to/repo.git"),
                                                  Arguments.of("git@site.com:user/project.git", "https://site.com/user/project.git"),
                                                  Arguments.of("ssh://user@site.com:port/path/to/repo.git", "https://site.com/path/to/repo.git"))
  }
}
