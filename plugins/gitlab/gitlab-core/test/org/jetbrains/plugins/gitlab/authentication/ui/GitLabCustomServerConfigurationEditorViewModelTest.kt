// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import org.jetbrains.plugins.gitlab.authentication.CustomServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class GitLabCustomServerConfigurationEditorViewModelTest {
  val editorVM = GitLabCustomServerConfigurationEditorViewModel(null)

  @AfterEach
  fun tearDown() {
    editorVM.serverUri.value = ""
    editorVM.clientId.value = ""
    editorVM.rootNamespaceId.value = ""
  }

  @Test
  fun `a self-managed server accepts a client id without a root namespace id`() {
    editorVM.apply {
      serverUri.value = SELF_MANAGED
      clientId.value = "abc"
      rootNamespaceId.value = ""
    }

    assertEquals(CustomServer(SELF_MANAGED, "abc", null), editorVM.buildServer())
  }

  @Test
  fun `a self-managed server keeps the client id and the root namespace id`() {
    editorVM.apply {
      serverUri.value = SELF_MANAGED
      clientId.value = "abc"
      rootNamespaceId.value = "456"
    }

    assertEquals(CustomServer(SELF_MANAGED, "abc", 456), editorVM.buildServer())
  }

  @Test
  fun `GitLab dot com accepts a root namespace id without a client id`() {
    for (id in listOf("", "  \t  ")) {
      editorVM.apply {
        serverUri.value = GITLAB_COM
        clientId.value = id
        rootNamespaceId.value = "123"
      }

      assertNull(editorVM.clientId.validationError)
      assertEquals(CustomServer(GITLAB_COM, null, 123), editorVM.buildServer())
    }
  }

  @Test
  fun `GitLab dot com requires a root namespace id`() {
    editorVM.apply {
      serverUri.value = GITLAB_COM
      clientId.value = "abc"
      rootNamespaceId.value = ""
    }

    assertNotNull(editorVM.rootNamespaceId.validationError)
    assertNull(editorVM.buildServer())
  }

  @Test
  fun `the root namespace id error follows the server URI`() {
    editorVM.apply {
      serverUri.value = SELF_MANAGED
      clientId.value = "abc"
      rootNamespaceId.value = ""
    }
    assertNull(editorVM.rootNamespaceId.validationError)

    editorVM.serverUri.value = GITLAB_COM
    assertNotNull(editorVM.rootNamespaceId.validationError)

    editorVM.serverUri.value = SELF_MANAGED
    assertNull(editorVM.rootNamespaceId.validationError)
  }

  @Test
  fun `a self-managed server requires a client id`() {
    editorVM.apply {
      serverUri.value = SELF_MANAGED
      clientId.value = ""
      rootNamespaceId.value = "123"
    }

    assertNotNull(editorVM.clientId.validationError)
    assertNull(editorVM.buildServer())
  }

  @Test
  fun `the client id error follows the server URI`() {
    editorVM.apply {
      serverUri.value = SELF_MANAGED
      clientId.value = ""
      rootNamespaceId.value = "123"
    }
    assertNotNull(editorVM.clientId.validationError)

    editorVM.serverUri.value = GITLAB_COM
    assertNull(editorVM.clientId.validationError)

    editorVM.serverUri.value = SELF_MANAGED
    assertNotNull(editorVM.clientId.validationError)
  }

  @Test
  fun `GitLab dot com without a root namespace id shows only the root namespace id error`() {
    editorVM.apply {
      serverUri.value = GITLAB_COM
      clientId.value = ""
      rootNamespaceId.value = ""
    }

    assertNull(editorVM.clientId.validationError)
    assertNotNull(editorVM.rootNamespaceId.validationError)
    assertNull(editorVM.buildServer())
  }

  @Test
  fun `an invalid root namespace id is rejected`() {
    for (id in listOf("abc", "0", "-1", "1.5", "9999999999999")) {
      editorVM.apply {
        serverUri.value = SELF_MANAGED
        clientId.value = "abc"
        rootNamespaceId.value = id
      }

      assertNotNull(editorVM.rootNamespaceId.validationError, id)
      assertNull(editorVM.buildServer(), id)
    }
  }

  @Test
  fun `an edit without changes doesn't change the record`() {
    val initial = CustomServer(SELF_MANAGED, "abc", 456)
    val vm = GitLabCustomServerConfigurationEditorViewModel(initial)

    assertNull(vm.serverUri.validationError)
    assertEquals(initial, vm.buildServer())
  }

  @Test
  fun `the editor shows the values of the initial server`() {
    val vm = GitLabCustomServerConfigurationEditorViewModel(CustomServer(GITLAB_COM, null, 123))

    assertEquals(GITLAB_COM, vm.serverUri.value)
    assertEquals("", vm.clientId.value)
    assertEquals("123", vm.rootNamespaceId.value)
  }

  companion object {
    private const val GITLAB_COM = "https://gitlab.com"
    private const val SELF_MANAGED = "https://gitlab.example.com"
  }
}
