// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URL

/**
 * Tests for host matching logic used by GithubApiRequestExecutor to decide whether to attach the token
 * to a particular request URL. We validate it directly via isAuthorizedUrl
 */
class GithubApiRequestExecutorAuthTest {

  private fun serverPath(host: String, useHttp: Boolean? = null, port: Int? = null): GithubServerPath {
    return GithubServerPath(useHttp, host, port, null)
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://github.com/actions/cache",
    "https://api.github.com/repos/actions/cache/contents",
    "https://raw.githubusercontent.com/actions/cache/v4/action.yml"
  ])
  fun `given github_com server, authorized URLs are accepted`(url: String) {
    val sp = serverPath("github.com")
    assertTrue(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL(url)))
  }

  @Test
  fun `given github_com server, unrelated URL is rejected`() {
    val sp = serverPath("github.com")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL("https://example.com/foo")))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://ghe.mycorp.local/myorg/repo",
    "https://raw.ghe.mycorp.local/myorg/repo/v1/action.yml",
    "https://ghe.mycorp.local/myorg/repo/raw/v1/action.yml"
  ])
  fun `given GHE server, main and raw subdomain URLs are accepted`(url: String) {
    val sp = serverPath("ghe.mycorp.local")
    assertTrue(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL(url)))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://api.github.com/repos/foo/bar",
    "https://raw.githubusercontent.com/myorg/repo/main/action.yml"
  ])
  fun `given GHE server, github_com URLs are rejected`(url: String) {
    val sp = serverPath("ghe.mycorp.local")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL(url)))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://myorg.ghe.com/myorg/repo",
    "https://api.myorg.ghe.com/repos/foo/bar",
    "https://raw.githubusercontent.com/myorg/repo/main/workflow.yml"
  ])
  fun `given GHE Data Residency server, its main, api-prefixed, and raw content URLs are accepted`(url: String) {
    val host = "myorg.ghe.com" // matches data residency condition
    val sp = serverPath(host)
    assertTrue(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL(url)))
  }

  @Test
  fun `given GHE Data Residency server, unrelated URL is rejected`() {
    val host = "myorg.ghe.com"
    val sp = serverPath(host)
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL("https://example.internal/foo")))
  }

  @Test
  fun `protocol and port must match serverPath`() {
    val host = "ghe.dev.local"
    val port = 8443
    val sp = serverPath(host, useHttp = false, port = port)

    assertTrue(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL("https://$host:$port/api/v3")))
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL("http://$host:$port/api/v3")), "HTTP should be rejected when HTTPS is expected")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(sp, URL("https://$host/api/v3")), "URL with different port should be rejected")
  }


  @ParameterizedTest
  @ValueSource(strings = [
    "https://github.com.malicious.com/foo",
    "https://api.github.com.malicious.com/repos/foo/bar",
    "https://raw.githubusercontent.com.malicious.com/org/repo/file.yml"
  ])
  fun `malicious hosts for github_com are rejected`(url: String) {
    val spGithub = serverPath("github.com")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(spGithub, URL(url)))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://ghe.mycorp.local.malicious.com/org/repo/file",
    "https://raw.ghe.mycorp.local.malicious.com/org/repo/file",
    "https://raw.ghe.mycorp.malicious.local/org/repo/file",
    "https://raw.ghe.malicious.mycorp.local.com/org/repo/file",
    "https://raw.malicious.ghe.mycorp.local.com/org/repo/file"
  ])
  fun `malicious hosts for GHE are rejected`(url: String) {
    val spEnterprise = serverPath("ghe.mycorp.local")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(spEnterprise, URL(url)))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "https://api.myorg.ghe.com.malicious.com/repos/foo/bar",
    "https://api.myorg.malicious.ghe.com/repos/foo/bar",
    "https://api.malicious.myorg.ghe.com/repos/foo/bar",
    "https://raw.githubusercontent.com.malicious.com/org/repo/file",
    "https://raw.malicious.githubusercontent.com/org/repo/file"
  ])
  fun `malicious hosts for GHE Data Residency are rejected`(url: String) {
    val spDr = serverPath("myorg.ghe.com") // match Data Residency
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(spDr, URL(url)))
  }


  @ParameterizedTest
  @ValueSource(strings = [
    "http://github.com/actions/cache",
    "http://api.github.com/repos/foo/bar",
    "http://raw.githubusercontent.com/org/repo/file.yml"
  ])
  fun `http protocol is not authorized by default for github_com`(url: String) {
    val spGithub = serverPath("github.com")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(spGithub, URL(url)))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "http://ghe.mycorp.local/myorg/repo",
    "http://raw.ghe.mycorp.local/myorg/repo/v1/action.yml"
  ])
  fun `http protocol is not authorized by default for GHE`(url: String) {
    val spEnterprise = serverPath("ghe.mycorp.local")
    assertFalse(GithubApiRequestExecutor.isAuthorizedUrl(spEnterprise, URL(url)))
  }
}