// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitLabProjectPathTest {

  @Test
  fun `extractProjectPath with valid path`() {
    val result = GitLabProjectPath.extractProjectPath("owner/project")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("owner")
    assertThat(result?.name).isEqualTo("project")
  }

  @Test
  fun `extractProjectPath with nested owner path`() {
    val result = GitLabProjectPath.extractProjectPath("group/subgroup/project")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("group/subgroup")
    assertThat(result?.name).isEqualTo("project")
  }

  @Test
  fun `extractProjectPath with deeply nested path`() {
    val result = GitLabProjectPath.extractProjectPath("org/team/subteam/project")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("org/team/subteam")
    assertThat(result?.name).isEqualTo("project")
  }

  @Test
  fun `extractProjectPath with no slash returns null`() {
    val result = GitLabProjectPath.extractProjectPath("project")
    assertThat(result).isNull()
  }

  @Test
  fun `extractProjectPath with empty string returns null`() {
    val result = GitLabProjectPath.extractProjectPath("")
    assertThat(result).isNull()
  }

  @Test
  fun `extractProjectPath with only slash returns null`() {
    val result = GitLabProjectPath.extractProjectPath("/")
    assertThat(result).isNull()
  }

  @Test
  fun `extractProjectPath with trailing slash returns null`() {
    val result = GitLabProjectPath.extractProjectPath("owner/project/")
    assertThat(result).isNull()
  }

  @Test
  fun `extractProjectPath with leading slash`() {
    val result = GitLabProjectPath.extractProjectPath("/owner/project")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("/owner")
    assertThat(result?.name).isEqualTo("project")
  }

  @Test
  fun `extractProjectPath with empty owner returns null`() {
    val result = GitLabProjectPath.extractProjectPath("/project")
    assertThat(result).isNull()
  }

  @Test
  fun `extractProjectPath with whitespace in path`() {
    val result = GitLabProjectPath.extractProjectPath("owner with spaces/project name")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("owner with spaces")
    assertThat(result?.name).isEqualTo("project name")
  }

  @Test
  fun `extractProjectPath with special characters`() {
    val result = GitLabProjectPath.extractProjectPath("owner-name/project.name")
    assertThat(result).isNotNull
    assertThat(result?.owner).isEqualTo("owner-name")
    assertThat(result?.name).isEqualTo("project.name")
  }
}
