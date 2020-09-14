// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.junit.Test

class GithubServerPathTest : UsefulTestCase() {

  @Test
  fun testDotComRemotes() {
    match("github.com", "github.com")
    match("github.com:8080", "github.com")
    match("gitHUB.com", "github.com")
    match("github.com", "gitHUB.com")

    noMatch("api.github.com", "github.com")
    noMatch("mygithub.com", "github.com")
    noMatch("github.commercial.eu", "github.com")
    noMatch("rnggithub.commercial.eu", "github.com")
  }

  @Test
  fun testEnterpriseRemotes() {
    match("ghe.labs.intellij.net", "ghe.labs.intellij.net")
    match("someserver.net/ghe", "someserver.net", "ghe/user/repo.git")

    noMatch("ghe.labs.net", "ghe.labs.intellij.net")
    noMatch("ghe.labs.net", "ghe.labs.network.com")
    noMatch("someserver.net", "someserver.net", "ghe/user/repo.git")
    noMatch("someserver.net/ghe", "someserver.net")
  }


  private fun match(serverUri: String, host: String, path: String = "user/repo.git") = checkMatch(true, serverUri, host, path)
  private fun noMatch(serverUri: String, host: String, path: String = "user/repo.git") = checkMatch(false, serverUri, host, path)

  private fun checkMatch(shouldMatch: Boolean, serverUri: String, host: String, path: String) {
    val serverPath = GithubServerPath.from(serverUri)

    val httpRemote = "http://$host/$path"
    val httpsRemote = "https://$host/$path"
    val gitRemote = "git://$host/$path"
    val sshRemote = "ssh://$host/$path"
    val sshUserRemote = "ssh://username@$host/$path"
    val scpRemote = "username@$host:$path"

    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $httpRemote", shouldMatch,
                          serverPath.matches(httpRemote))
    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $httpsRemote", shouldMatch,
                          serverPath.matches(httpsRemote))
    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $gitRemote", shouldMatch,
                          serverPath.matches(gitRemote))
    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $sshRemote", shouldMatch,
                          serverPath.matches(sshRemote))
    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $sshUserRemote", shouldMatch,
                          serverPath.matches(sshUserRemote))
    TestCase.assertEquals("$serverUri should ${if (!shouldMatch) "NOT " else ""}match $scpRemote", shouldMatch,
                          serverPath.matches(scpRemote))
  }
}