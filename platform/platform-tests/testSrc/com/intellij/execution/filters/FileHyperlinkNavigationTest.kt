// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class FileHyperlinkNavigationTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testOpenableFileUsesNavigationServiceCompatibilityPath() {
    val navigationService = RecordingNavigationService()
    project.replaceService(NavigationService::class.java, navigationService, testRootDisposable)
    val descriptor = OpenFileDescriptor(project, LightVirtualFile("Main.txt", "hello"), 0)

    val handled = runBlocking(Dispatchers.Default) {
      navigateFileHyperlink(project, descriptor, useBrowser = true)
    }

    assertTrue(handled)
    assertEquals(0, navigationService.requestCalls)
    assertEquals(1, navigationService.navigatableCalls)
    assertSame(descriptor, navigationService.lastNavigatables.single())
  }

  fun testInProjectDirectoryUsesNavigationServiceRequestPath() {
    val navigationService = RecordingNavigationService()
    project.replaceService(NavigationService::class.java, navigationService, testRootDisposable)
    val directory = myFixture.tempDirFixture.findOrCreateDir("src")
    val descriptor = OpenFileDescriptor(project, directory)

    val handled = runBlocking(Dispatchers.Default) {
      navigateFileHyperlink(project, descriptor, useBrowser = true)
    }

    assertTrue(handled)
    assertEquals(1, navigationService.requestCalls)
    assertEquals(0, navigationService.navigatableCalls)
    assertNotNull(navigationService.lastRequest)
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
