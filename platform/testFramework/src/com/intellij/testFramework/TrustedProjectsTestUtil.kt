// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.ide.trustedProjects.TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly

object TrustedProjectsTestUtil {
  /**
   * Runs [action] with the trusted-projects check enabled: in a headless run every project is implicitly trusted
   * (`idea.trust.headless.disabled` defaults to `true`, see `TrustedProjects.isTrustedCheckDisabled`), which masks
   * the untrusted-project behavior under test.
   *
   * Scope this to the test body only: a project with no recorded trust answer cannot be *opened* while the check is
   * enabled (a headless open is aborted), so a fixture must open its project before this is entered. For the same
   * reason an explicitly recorded `false` outlives the scope in the application-level state - record `true` back
   * before leaving [action].
   */
  @TestOnly
  @JvmStatic
  fun withTrustedProjectsCheckEnabled(action: ThrowableRunnable<out Throwable>) {
    @Suppress("UNCHECKED_CAST")
    PlatformTestUtil.withSystemProperty(TRUST_HEADLESS_DISABLED_PROPERTY, "false", action as ThrowableRunnable<Throwable>)
  }

  /** Enables the trusted-projects check until [parentDisposable] is disposed. */
  @TestOnly
  @JvmStatic
  fun enableTrustedProjectsCheck(parentDisposable: Disposable) {
    setSystemProperty(TRUST_HEADLESS_DISABLED_PROPERTY, "false", parentDisposable)
  }
}
