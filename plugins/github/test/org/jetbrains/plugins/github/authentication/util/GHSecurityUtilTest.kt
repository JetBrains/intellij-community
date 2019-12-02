// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import org.junit.Assert
import org.junit.Test

class GHSecurityUtilTest {

  @Test
  fun testEmpty() {
    checkBad("")
  }

  @Test
  fun testOnlyRepo() {
    checkBad("repo")
  }

  @Test
  fun testOnlyRepoAndGist() {
    checkBad("repo, gist")
  }

  @Test
  fun testOnlyOrg() {
    checkBad("read:org")
  }

  @Test
  fun testOnlyAdminOrg() {
    checkBad("admin:org")
  }

  @Test
  fun testOnlyRequired() {
    checkGood("repo, gist, read:org")
  }

  @Test
  fun testOnlyRequiredUnordered() {
    checkGood("gist, read:org, repo")
  }

  @Test
  fun testOnlyRequiredWithAdminOrg() {
    checkGood("repo, gist, admin:org")
  }

  @Test
  fun testOnlyRequiredWithWriteOrg() {
    checkGood("repo, gist, write:org")
  }

  @Test
  fun testRequiredAndOthers() {
    checkGood("repo, gist, read:org, delete_repo, site_admin, read:gpg_key")
  }

  @Test
  fun testRequiredAndOthersUnordered() {
    checkGood("delete_repo, repo, site_admin, gist, read:gpg_key, read:org")
  }

  private fun checkGood(scopes: String) {
    val result = GHSecurityUtil.isEnoughScopes(scopes)
    Assert.assertTrue(result)
  }

  private fun checkBad(scopes: String) {
    val result = GHSecurityUtil.isEnoughScopes(scopes)
    Assert.assertFalse(result)
  }
}