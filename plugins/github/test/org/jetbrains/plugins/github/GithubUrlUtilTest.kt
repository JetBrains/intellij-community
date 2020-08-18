// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  fun testRemoveTrailingSlash() {
    val tests = TestCase<String>()

    tests.add("http://github.com/", "http://github.com")
    tests.add("http://github.com", "http://github.com")

    tests.add("http://github.com/user/repo/", "http://github.com/user/repo")
    tests.add("http://github.com/user/repo", "http://github.com/user/repo")

    runTestCase(tests) { `in` -> removeTrailingSlash(`in`) }
  }

  fun testRemoveProtocolPrefix() {
    val tests = TestCase<String>()

    tests.add("github.com/user/repo/", "github.com/user/repo/")
    tests.add("api.github.com/user/repo/", "api.github.com/user/repo/")

    tests.add("http://github.com/user/repo/", "github.com/user/repo/")
    tests.add("https://github.com/user/repo/", "github.com/user/repo/")
    tests.add("git://github.com/user/repo/", "github.com/user/repo/")
    tests.add("git@github.com:user/repo/", "github.com/user/repo/")

    tests.add("git@github.com:username/repo/", "github.com/username/repo/")
    tests.add("https://username:password@github.com/user/repo/", "github.com/user/repo/")
    tests.add("https://username@github.com/user/repo/", "github.com/user/repo/")
    tests.add("https://github.com:2233/user/repo/", "github.com:2233/user/repo/")

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com/user/repo/")
    tests.add("HttP://GitHub.com/user/repo/", "GitHub.com/user/repo/")

    runTestCase(tests) { `in` -> removeProtocolPrefix(`in`) }
  }

  fun testRemovePort() {
    val tests = TestCase<String>()

    tests.add("github.com/user/repo/", "github.com/user/repo/")
    tests.add("github.com", "github.com")
    tests.add("github.com/", "github.com/")

    tests.add("github.com:80/user/repo/", "github.com/user/repo/")
    tests.add("github.com:80/user/repo", "github.com/user/repo")
    tests.add("github.com:80/user", "github.com/user")
    tests.add("github.com:80", "github.com")

    runTestCase(tests) { `in` -> removePort(`in`) }
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

  fun testGetApiUrl() {
    val tests = TestCase<String>()

    tests.add("github.com", "https://api.github.com")
    tests.add("https://github.com/", "https://api.github.com")

    tests.add("https://my.site.com/", "https://my.site.com/api/v3")
    tests.add("https://api.site.com/", "https://api.site.com/api/v3")
    tests.add("https://url.github.com/", "https://url.github.com/api/v3")

    tests.add("my.site.com/", "https://my.site.com/api/v3")
    tests.add("api.site.com/", "https://api.site.com/api/v3")
    tests.add("url.github.com/", "https://url.github.com/api/v3")

    tests.add("http://my.site.com/", "http://my.site.com/api/v3")
    tests.add("http://api.site.com/", "http://api.site.com/api/v3")
    tests.add("http://url.github.com/", "http://url.github.com/api/v3")

    tests.add("HTTP://GITHUB.com", "http://api.github.com")
    tests.add("HttP://GitHub.com/", "http://api.github.com")

    tests.add("https://ghe.com/suffix", "https://ghe.com/suffix/api/v3")
    tests.add("https://ghe.com/suFFix", "https://ghe.com/suFFix/api/v3")

    runTestCase(tests) { `in` -> getApiUrl(`in`) }
  }

  fun testUri() {
    val tests = TestCase<URI?>()

    tests.add("https://github.com", URI("https", "github.com", null, null))
    tests.add("https://api.github.com", URI("https", "api.github.com", null, null))
    tests.add("https://github.com/", URI("https", "github.com", null, null))
    tests.add("https://api.github.com/", URI("https", "api.github.com", null, null))

    tests.add("https://github.com/user/repo/", URI("https", "github.com", "/user/repo", null))
    tests.add("https://api.github.com/user/repo/", URI("https", "api.github.com", "/user/repo", null))

    tests.add("http://github.com/user/repo/", URI("http", "github.com", "/user/repo", null))
    tests.add("https://github.com/user/repo/", URI("https", "github.com", "/user/repo", null))
    tests.add("git://github.com/user/repo/", URI("git", "github.com", "/user/repo", null))
    tests.add("ssh://user@github.com/user/repo/", URI("ssh", "user", "github.com", -1, "/user/repo", null, null))

    tests.add("https://username:password@github.com/user/repo/",
              URI("https", "username:password", "github.com", -1, "/user/repo", null, null))
    tests.add("https://username@github.com/user/repo/", URI("https", "username", "github.com", -1, "/user/repo", null, null))
    tests.add("https://github.com:2233/user/repo/", URI("https", null, "github.com", 2233, "/user/repo", null, null))

    tests.add("HTTP://GITHUB.com/user/repo/", URI("HTTP", null, "GITHUB.com", -1, "/user/repo", null, null))
    tests.add("HttP://GitHub.com/user/repo/", URI("HttP", null, "GitHub.com", -1, "/user/repo", null, null))

    tests.add("git@github.com:user/repo/", URI("https", null, "github.com", -1, "/user/repo", null, null))

    runTestCase(tests) { `in` -> getUriFromRemoteUrl(`in`) }
  }
}
