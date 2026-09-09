// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GitHostingUrlUtilTest {
  private fun <T> checkStringConversion(mapping: List<Pair<String, T>>, mapper: (String) -> T) {
    for ((initial, expected) in mapping) {
      val actual = mapper(initial)
      assertThat(actual).describedAs(initial).isEqualTo(expected)
    }
  }

  @Test
  fun isSshUrl() {
    checkStringConversion(
      listOf(
        "ssh://git@example.com/user/repo.git" to true,
        "git+ssh://git@example.com/user/repo.git" to true,
        "ssh+git://git@example.com/user/repo.git" to true,
        "git@example.com:user/repo.git" to true,
        "example.com:user/repo.git" to true,
        "[::1]:user/repo.git" to true,
        "https://example.com/user/repo.git" to false,
        "http://example.com/user/repo.git" to false,
        "git://example.com/user/repo.git" to false,
        "file:///path/to/repo.git" to false,
        "/path/to/repo.git" to false,
        "path/to/repo.git" to false,
        "C:" to false,
        "C:/path/to/repo.git" to false,
        "C:\\path\\to\\repo.git" to false,
      ),
      GitHostingUrlUtil::isSshUrl,
    )
  }

  @Test
  fun removeProtocolPrefix() {
    checkStringConversion(
      listOf(
        "github.com/user/repo/" to "github.com/user/repo/",
        "api.github.com/user/repo/" to "api.github.com/user/repo/",
        "http://github.com/user/repo/" to "github.com/user/repo/",
        "https://github.com/user/repo/" to "github.com/user/repo/",
        "git://github.com/user/repo/" to "github.com/user/repo/",
        "git@github.com:user/repo/" to "github.com:user/repo/",
        "git@github.com:username/repo/" to "github.com:username/repo/",
        "git@github.com:/username/repo/" to "github.com:/username/repo/",
        "https://username:password@github.com/user/repo/" to "github.com/user/repo/",
        "https://username@github.com/user/repo/" to "github.com/user/repo/",
        "https://github.com:2233/user/repo/" to "github.com:2233/user/repo/",
        "HTTP://GITHUB.com/user/repo/" to "GITHUB.com/user/repo/",
        "HttP://GitHub.com/user/repo/" to "GitHub.com/user/repo/",
      ),
      GitHostingUrlUtil::removeProtocolPrefix
    )
  }

  @Test
  fun getUriFromRemoteUrl() {
    checkStringConversion(listOf(
      "https://github.com" to URI("https", "github.com", null, null),
      "https://api.github.com" to URI("https", "api.github.com", null, null),
      "https://github.com/" to URI("https", "github.com", null, null),
      "https://api.github.com/" to URI("https", "api.github.com", null, null),
      "https://github.com/user/repo/" to URI("https", "github.com", "/user/repo", null),
      "https://api.github.com/user/repo/" to URI("https", "api.github.com", "/user/repo", null),
      "http://github.com/user/repo/" to URI("http", "github.com", "/user/repo", null),
      "https://github.com/user/repo/" to URI("https", "github.com", "/user/repo", null),
      "git://github.com/user/repo/" to URI("git", "github.com", "/user/repo", null),
      "ssh://user@github.com/user/repo/" to URI("ssh", "user", "github.com", -1, "/user/repo", null, null),
      "https://username:password@github.com/user/repo/" to URI("https", "username:password", "github.com", -1, "/user/repo", null, null),
      "https://username@github.com/user/repo/" to URI("https", "username", "github.com", -1, "/user/repo", null, null),
      "https://github.com:2233/user/repo/" to URI("https", null, "github.com", 2233, "/user/repo", null, null),
      "HTTP://GITHUB.com/user/repo/" to URI("HTTP", null, "GITHUB.com", -1, "/user/repo", null, null),
      "HttP://GitHub.com/user/repo/" to URI("HttP", null, "GitHub.com", -1, "/user/repo", null, null),
      "git@github.com:user/repo/" to URI("https", null, "github.com", -1, "/user/repo", null, null),
      "git@github.com:/user/repo/" to URI("https", null, "github.com", -1, "/user/repo", null, null),
      "ssh://git@git.jetbrains.space/company/project/repository.git" to URI("ssh", "git", "git.jetbrains.space", -1,
                                                                            "/company/project/repository", null, null),
      "https://git.jetbrains.space/company/project/repository.git" to URI("https", null, "git.jetbrains.space", -1,
                                                                          "/company/project/repository", null, null)
    ), GitHostingUrlUtil::getUriFromRemoteUrl)
  }

  @Test
  fun testMatch() {
    match("github.com", "github.com")
    match("github.com:8080", "github.com")
    match("gitHUB.com", "github.com")
    match("github.com", "gitHUB.com")

    noMatch("api.github.com", "github.com")
    noMatch("mygithub.com", "github.com")
    noMatch("github.commercial.eu", "github.com")
    noMatch("rnggithub.commercial.eu", "github.com")

    match("ghe.labs.intellij.net", "ghe.labs.intellij.net")
    match("someserver.net/ghe", "someserver.net/ghe")
    match("someserver.net", "someserver.net/ghe")

    noMatch("ghe.labs.net", "ghe.labs.intellij.net")
    noMatch("ghe.labs.net", "ghe.labs.network.com")
    noMatch("someserver.net/ghe", "someserver.net")
  }


  private fun match(serverHost: String, host: String, path: String = "user/repo.git") = checkMatch(true, serverHost, host, path)
  private fun noMatch(serverHost: String, host: String, path: String = "user/repo.git") = checkMatch(false, serverHost, host, path)

  private fun checkMatch(shouldMatch: Boolean, serverUri: String, host: String, path: String) {
    val uri = URI.create("https://$serverUri")

    val httpServer = "http://$host"
    val httpsServer = "https://$host"
    val gitServer = "git://$host"
    val sshServer = "ssh://$host"
    val sshUserServer = "ssh://username@$host"

    assertMatch(shouldMatch, serverUri, uri, httpServer)
    assertMatch(shouldMatch, serverUri, uri, httpsServer)
    assertMatch(shouldMatch, serverUri, uri, gitServer)
    assertMatch(shouldMatch, serverUri, uri, sshServer)
    assertMatch(shouldMatch, serverUri, uri, sshUserServer)

    val httpRemote = "http://$host/$path"
    val httpsRemote = "https://$host/$path"
    val gitRemote = "git://$host/$path"
    val sshRemote = "ssh://$host/$path"
    val sshUserRemote = "ssh://username@$host/$path"
    val scpRemote = "username@$host:$path"

    assertMatch(shouldMatch, serverUri, uri, httpRemote)
    assertMatch(shouldMatch, serverUri, uri, httpsRemote)
    assertMatch(shouldMatch, serverUri, uri, gitRemote)
    assertMatch(shouldMatch, serverUri, uri, sshRemote)
    assertMatch(shouldMatch, serverUri, uri, sshUserRemote)
    assertMatch(shouldMatch, serverUri, uri, scpRemote)
  }

  private fun assertMatch(shouldMatch: Boolean, serverUri: String, uri: URI, remoteUrl: String) {
    val message = "$serverUri should ${if (!shouldMatch) "not " else ""}match $remoteUrl"
    assertThat(GitHostingUrlUtil.match(uri, remoteUrl)).describedAs(message).isEqualTo(shouldMatch)
  }
}