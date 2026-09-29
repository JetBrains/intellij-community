// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.ide.trustedProjects.TrustedProjectsStateProvider
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ThreeState
import com.intellij.util.application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TrustedProjectsStateProviderTest {

  private val ownedRoot = Path.of("project/provider-owned")
  private val otherRoot = Path.of("project/provider-other")
  private val provider = RecordingProvider(ownedRoot)

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    TrustedProjectsStateProvider.EP_NAME.point.registerExtension(provider, disposable)
  }

  @Test
  @SystemProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, "false")
  fun `a provider records and answers for the project it owns`() {
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(ownedRoot))

    TrustedProjects.setProjectTrusted(ownedRoot, true)

    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(ownedRoot))
    Assertions.assertEquals(ThreeState.UNSURE, TrustedPaths.getInstance().getProjectPathTrustedState(ownedRoot)) {
      "The owned project must not go into TrustedPaths"
    }
  }

  @Test
  @SystemProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, "false")
  fun `the default provider records a project that no other provider is applicable to in TrustedPaths`() {
    TrustedProjects.setProjectTrusted(otherRoot, false)

    Assertions.assertNull(provider.state) { "The provider must not record a project it does not own" }
    Assertions.assertEquals(ThreeState.NO, TrustedPaths.getInstance().getProjectPathTrustedState(otherRoot))
    Assertions.assertEquals(ThreeState.NO, TrustedProjects.getProjectTrustedState(otherRoot))
    TrustedProjects.setProjectTrusted(otherRoot, true)
  }

  @Test
  @SystemProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, "false")
  fun `a change in the provider notifies the listeners once`() {
    val events = mutableListOf<Boolean>()
    application.messageBus.connect(disposable).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) {
        events += true
      }

      override fun onProjectUntrusted(locatedProject: LocatedProject) {
        events += false
      }
    })

    TrustedProjects.setProjectTrusted(ownedRoot, true)
    TrustedProjects.setProjectTrusted(ownedRoot, true)
    TrustedProjects.setProjectTrusted(ownedRoot, false)

    Assertions.assertEquals(listOf(true, false), events)
  }

  @Test
  fun `the headless bypass outranks a provider`() {
    provider.state = false

    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(ownedRoot))
  }

  private class RecordingProvider(private val ownedRoot: Path) : TrustedProjectsStateProvider {
    var state: Boolean? = null

    override fun isApplicable(locatedProject: LocatedProject): Boolean = locatedProject.projectRoots == listOf(ownedRoot)

    override fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState {
      return state?.let { ThreeState.fromBoolean(it) } ?: ThreeState.UNSURE
    }

    override fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean) {
      state = isTrusted
    }
  }
}
