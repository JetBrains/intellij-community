// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import org.jetbrains.plugins.github.api.GithubServerPath
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test

class GHPersistentAccountsTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Test
  fun `test serialize`() {
    val service = GHPersistentAccounts()

    val id1 = "231775c2-04c4-4819-b04a-d9d21a361b7f"
    val id2 = "cdd614b1-0511-4f82-b9e2-6625ff5aa059"
    val n1 = "test1"
    val n2 = "test2"
    service.accounts = setOf(
      GithubAccount(n1, GithubServerPath.DEFAULT_SERVER, id1),
      GithubAccount(n2, GithubServerPath(false, "ghe.labs.intellij.net", 80, "sfx"), id2)
    )
    @Suppress("UsePropertyAccessSyntax")
    val state = service.getState()
    val element = serialize(state)!!
    val xml = JDOMUtil.write(element)
    assertEquals("""
      <array>
        <account name="$n1" id="$id1">
          <server host="github.com" />
        </account>
        <account name="$n2" id="$id2">
          <server useHttp="false" host="ghe.labs.intellij.net" port="80" suffix="sfx" />
        </account>
      </array>
    """.trimIndent(), xml)
  }

  @Test
  fun `test deserialize`() {
    val service = GHPersistentAccounts()

    val id1 = "231775c2-04c4-4819-b04a-d9d21a361b7f"
    val id2 = "cdd614b1-0511-4f82-b9e2-6625ff5aa059"
    val n1 = "test1"
    val n2 = "test2"

    val element = JDOMUtil.load("""
      <component name="GithubAccounts">
        <account name="$n1" id="$id1">
          <server host="github.com" />
        </account>
        <account name="$n2" id="$id2">
          <server useHttp="false" host="ghe.labs.intellij.net" port="80" suffix="sfx" />
        </account>
      </component>
    """.trimIndent())
    deserializeAndLoadState(service, element)
    assertEquals(setOf(
      GithubAccount("test1", GithubServerPath.DEFAULT_SERVER, id1),
      GithubAccount("test2", GithubServerPath(false, "ghe.labs.intellij.net", 80, "sfx"), id2)
    ), service.accounts)
  }
}