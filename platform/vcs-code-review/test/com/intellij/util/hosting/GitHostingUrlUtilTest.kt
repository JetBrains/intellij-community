// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.hosting

import junit.framework.Assert.assertEquals
import org.junit.Test
import java.net.URI

internal class GitHostingUrlUtilTest {
  private fun <T> checkStringConversion(mapping: List<Pair<String, T>>, mapper: (String) -> T) {
    for ((initial, expected) in mapping) {
      val actual = mapper(initial)
      assertEquals(initial, expected, actual)
    }
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
        "git@github.com:user/repo/" to "github.com/user/repo/",
        "git@github.com:username/repo/" to "github.com/username/repo/",
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
      "ssh://git@git.jetbrains.space/company/project/repository.git" to URI("ssh", "git", "git.jetbrains.space", -1, "/company/project/repository", null, null),
      "https://git.jetbrains.space/company/project/repository.git" to URI("https", null, "git.jetbrains.space", -1, "/company/project/repository", null, null)
    ), GitHostingUrlUtil::getUriFromRemoteUrl)
  }
}