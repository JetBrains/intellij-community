// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.util.Pair
import junit.framework.TestCase
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.util.GithubUrlUtil.*
import java.net.URI
import java.util.*

class GithubUrlUtilTest : TestCase() {
  private class TestCase<T> {
    val tests: MutableList<Pair<String, T>> = ArrayList()

    fun add(`in`: String, out: T?) {
      tests.add(Pair.create(`in`, out))
    }

  }

  private fun <T> runTestCase(tests: TestCase<T>, func: (String) -> T) {
    for (test in tests.tests) {
      val input = test.getFirst()
      val expectedResult = test.getSecond()
      val result = func(input)
      assertEquals(input, expectedResult, result)
    }
  }

  fun testGetUserAndRepositoryFromRemoteUrl() {
    val tests = TestCase<GHRepositoryPath?>()

    tests.add("http://github.com/username/reponame/", GHRepositoryPath("username", "reponame"))
    tests.add("https://github.com/username/reponame/", GHRepositoryPath("username", "reponame"))
    tests.add("git://github.com/username/reponame/", GHRepositoryPath("username", "reponame"))
    tests.add("git@github.com:username/reponame/", GHRepositoryPath("username", "reponame"))

    tests.add("https://github.com/username/reponame", GHRepositoryPath("username", "reponame"))
    tests.add("https://github.com/username/reponame.git", GHRepositoryPath("username", "reponame"))
    tests.add("https://github.com/username/reponame.git/", GHRepositoryPath("username", "reponame"))
    tests.add("git@github.com:username/reponame.git/", GHRepositoryPath("username", "reponame"))

    tests.add("http://login:passsword@github.com/username/reponame/",
              GHRepositoryPath("username", "reponame"))

    tests.add("HTTPS://GitHub.com/username/reponame/", GHRepositoryPath("username", "reponame"))
    tests.add("https://github.com/UserName/RepoName/", GHRepositoryPath("UserName", "RepoName"))

    tests.add("https://github.com/RepoName/", null)
    tests.add("git@github.com:user/", null)
    tests.add("https://user:pass@github.com/", null)

    runTestCase(tests) { `in` -> getUserAndRepositoryFromRemoteUrl(`in`) }
  }

  fun testMakeGithubRepoFromRemoteUrl() {
    val tests = TestCase<String?>()

    tests.add("http://github.com/username/reponame/", "https://github.com/username/reponame")
    tests.add("https://github.com/username/reponame/", "https://github.com/username/reponame")
    tests.add("git://github.com/username/reponame/", "https://github.com/username/reponame")
    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame")

    tests.add("https://github.com/username/reponame", "https://github.com/username/reponame")
    tests.add("https://github.com/username/reponame.git", "https://github.com/username/reponame")
    tests.add("https://github.com/username/reponame.git/", "https://github.com/username/reponame")
    tests.add("git@github.com:username/reponame.git/", "https://github.com/username/reponame")

    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame")
    tests.add("http://login:passsword@github.com/username/reponame/", "https://github.com/username/reponame")

    tests.add("HTTPS://GitHub.com/username/reponame/", "https://github.com/username/reponame")
    tests.add("https://github.com/UserName/RepoName/", "https://github.com/UserName/RepoName")

    tests.add("https://github.com/RepoName/", null)
    tests.add("git@github.com:user/", null)
    tests.add("https://user:pass@github.com/", null)

    runTestCase(tests) { `in` -> makeGithubRepoUrlFromRemoteUrl(`in`, "https://github.com") }
  }

  fun testGetHostFromUrl() {
    val tests = TestCase<String>()

    tests.add("github.com", "github.com")
    tests.add("api.github.com", "api.github.com")
    tests.add("github.com/", "github.com")
    tests.add("api.github.com/", "api.github.com")

    tests.add("github.com/user/repo/", "github.com")
    tests.add("api.github.com/user/repo/", "api.github.com")

    tests.add("http://github.com/user/repo/", "github.com")
    tests.add("https://github.com/user/repo/", "github.com")
    tests.add("git://github.com/user/repo/", "github.com")
    tests.add("git@github.com:user/repo/", "github.com")

    tests.add("git@github.com:username/repo/", "github.com")
    tests.add("https://username:password@github.com/user/repo/", "github.com")
    tests.add("https://username@github.com/user/repo/", "github.com")
    tests.add("https://github.com:2233/user/repo/", "github.com")

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com")
    tests.add("HttP://GitHub.com/user/repo/", "GitHub.com")

    runTestCase(tests) { `in` -> getHostFromUrl(`in`) }
  }
}
