// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class GithubServerPathDataResidencyTest(
  private val host: String,
  private val isDataResidency: Boolean
) {
  companion object {
    @JvmStatic @get:Parameters("GQL API path for {0} should be {1}")
    val tests = arrayOf<Array<Any>>(
      arrayOf("github.com", false),
      arrayOf("okta.github.com", false),
      arrayOf("okta.ghe.com", true),
      arrayOf("bla.bla.com", false),
      arrayOf("bla.com", false),
      arrayOf("GHE.com", false),
      arrayOf("TENANT.GHE.COM", true),
      arrayOf("aghe.com", false),
    )
  }

  @Test
  fun `data residency is detected`() {
    assertEquals(isDataResidency, GithubServerPath(host).isGheDataResidency)
  }
}

@RunWith(Parameterized::class)
class GithubServerPathApiPathTest(
  private val host: String,
  private val apiPath: String
) {
  companion object {
    @JvmStatic @get:Parameters("REST API URL for {0} should be {1}")
    val tests = arrayOf<Array<Any>>(
      arrayOf("github.com", "https://api.github.com"),
      arrayOf("github.com/", "https://api.github.com"),
      arrayOf("agithub.com", "https://agithub.com/api/v3"),
      arrayOf("tenant.ghe.com", "https://api.tenant.ghe.com"),
      arrayOf("bla.com", "https://bla.com/api/v3"),
      arrayOf("bla.com/", "https://bla.com/api/v3"),
      arrayOf("bla.com/github-path", "https://bla.com/github-path/api/v3"),

      // Expected?
      arrayOf("github.com/enterprises/ent", "https://api.github.com/enterprises/ent"),
      arrayOf("tenant.ghe.com/enterprises/tenant", "https://api.tenant.ghe.com/enterprises/tenant"),
    )
  }

  @Test
  fun `api path is correct`() {
    assertEquals(apiPath, GithubServerPath.from(host).toApiUrl().toString())
  }
}

@RunWith(Parameterized::class)
class GithubServerPathGraphqlApiPathTest(
  private val host: String,
  private val apiPath: String
) {
  companion object {
    @JvmStatic @get:Parameters("GQL API URL for {0} should be {1}")
    val tests = arrayOf<Array<Any>>(
      arrayOf("github.com", "https://api.github.com/graphql"),
      arrayOf("github.com/", "https://api.github.com/graphql"),
      arrayOf("tenant.ghe.com", "https://api.tenant.ghe.com/graphql"),
      arrayOf("bla.com", "https://bla.com/api/graphql"),
      arrayOf("bla.com/", "https://bla.com/api/graphql"),
      arrayOf("bla.com/github-path", "https://bla.com/github-path/api/graphql"),

      // Expected?
      arrayOf("github.com/enterprises/ent", "https://api.github.com/enterprises/ent/graphql"),
      arrayOf("tenant.ghe.com/enterprises/tenant", "https://api.tenant.ghe.com/enterprises/tenant/graphql"),
    )
  }

  @Test
  fun `graphql api path is correct`() {
    assertEquals(apiPath, GithubServerPath.from(host).toGraphQLUrl().toString())
  }
}