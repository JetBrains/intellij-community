// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
class FileHyperlinkNavigationTest {
  private val testProject = projectFixture()
  private val testModule = testProject.moduleFixture()
  private val sourceRoot = testModule.sourceRootFixture()

  private val project get() = testProject.get()

  @Test
  fun `openable file uses navigation service compatibility path`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val navigationService = RecordingNavigationService()
    project.replaceService(NavigationService::class.java, navigationService, disposable)
    val descriptor = OpenFileDescriptor(project, LightVirtualFile("Main.txt", "hello"), 0)

    val handled = navigateFileHyperlink(project, descriptor, useBrowser = true)

    assertTrue(handled)
    assertEquals(0, navigationService.requestCalls)
    assertEquals(1, navigationService.navigatableCalls)
    assertSame(descriptor, navigationService.lastNavigatables.single())
  }

  @Test
  fun `in-project directory uses navigation service request path`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val navigationService = RecordingNavigationService()
    project.replaceService(NavigationService::class.java, navigationService, disposable)
    val directory = sourceRoot.get().virtualFile
    val descriptor = OpenFileDescriptor(project, directory)

    val handled = navigateFileHyperlink(project, descriptor, useBrowser = true)

    assertTrue(handled)
    assertEquals(1, navigationService.requestCalls)
    assertEquals(0, navigationService.navigatableCalls)
    assertNotNull(navigationService.lastRequest)
  }

  @Test
  fun `legacy in-project directory can navigate from EDT`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val directory = sourceRoot.get().virtualFile
    val descriptor = OpenFileDescriptor(project, directory)

    val handled = withContext(Dispatchers.EDT) {
      navigateFileHyperlinkLegacy(project, descriptor, useBrowser = true)
    }

    assertTrue(handled)
  }
}

private class RecordingNavigationService : NavigationService {
  var requestCalls: Int = 0
    private set
  var navigatableCalls: Int = 0
    private set
  var lastRequest: NavigationRequest? = null
    private set
  var lastNavigatables: List<Navigatable> = emptyList()
    private set

  override suspend fun navigate(dataContext: DataContext, options: NavigationOptions) {
    error("Unexpected data-context navigation")
  }

  override suspend fun navigate(request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?) {
    requestCalls++
    lastRequest = request
  }

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions, dataContext: DataContext?): Boolean {
    navigatableCalls++
    lastNavigatables = navigatables
    return true
  }
}
