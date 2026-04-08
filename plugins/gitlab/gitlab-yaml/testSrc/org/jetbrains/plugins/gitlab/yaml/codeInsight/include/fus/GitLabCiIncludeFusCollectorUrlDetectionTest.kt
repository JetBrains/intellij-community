// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GitLabCiIncludeFusCollectorUrlDetectionTest {

  private val collector = GitLabCiIncludeApplicationMetricsCollector()

  @Test
  fun `test http URLs are detected as remote`() {
    assertTrue(collector.guessIsGitLabCiRemoteUrl("http://example.com/ci-template.yml"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("http://gitlab.com/templates/template.yml"))
  }

  @Test
  fun `test https URLs are detected as remote`() {
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/ci-template.yml"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://gitlab.com/templates/template.yml"))
  }

  @Test
  fun `test URLs with environment variables are detected as remote`() {
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://\$MY_DOMAIN/template.yml"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("http://example.com/\$VERSION/template.yml"))
  }

  @Test
  fun `test file URLs are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl("file:///path/to/local/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("file://relative/path.yml"))
  }

  @Test
  fun `test local paths are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl("/path/to/local/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("relative/path/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("./relative/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("../parent/file.yml"))
  }

  @Test
  fun `test paths with wildcards are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl("/path/*/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("/path/**/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("subdir/**/.gitlab-ci.yml"))
  }

  @Test
  fun `test paths with environment variables are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl($$"/$MY_PATH/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl($$"$MY_PATH/file.yml"))
  }

  @Test
  fun `test other URI schemes are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl("ftp://example.com/file.yml"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("ssh://git@example.com/repo.git"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl("git://example.com/repo.git"))
  }

  @Test
  fun `test invalid URIs are not detected as remote`() {
    assertFalse(collector.guessIsGitLabCiRemoteUrl("not a valid uri"))
    assertFalse(collector.guessIsGitLabCiRemoteUrl(""))
  }

  @Test
  fun `test special characters in URLs`() {
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/path/with-dashes/file.yml"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/path_with_underscores/file.yml"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com:8080/file.yml"))
  }

  @Test
  fun `test URLs with query parameters and fragments`() {
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/file.yml?version=1.0"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/file.yml#section"))
    assertTrue(collector.guessIsGitLabCiRemoteUrl("https://example.com/file.yml?v=1&type=ci#top"))
  }
}
