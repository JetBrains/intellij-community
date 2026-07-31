// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for Project View frontend tests.
 *
 * It installs the [InProcessRemoteApiProviderService] before each test, so that the real
 * [com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator] talks to the real backend
 * Project View RPC implementation in-process (the DTO round-trip runs, but there is no serialization
 * and no real connection). Concrete tests drive a pane via [withProjectViewPane].
 *
 * Concrete subclasses must be annotated with `@TestApplication` (that also enables `@TestFixtures`) and
 * typically declare `projectFixture`/`moduleFixture`/`sourceRootFixture`s.
 */
internal abstract class AbstractProjectViewPaneTest {
  private lateinit var rpcDisposable: Disposable

  @BeforeEach
  fun installInProcessRpc() {
    rpcDisposable = Disposer.newDisposable("InProcessRemoteApiProviderService")
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      RemoteApiProviderService::class.java,
      InProcessRemoteApiProviderService(),
      rpcDisposable,
    )
  }

  @AfterEach
  fun uninstallInProcessRpc() {
    Disposer.dispose(rpcDisposable)
  }
}
