package com.intellij.syntax.psi.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.ExtensionSupport
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.registerExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class IntelliJExtensionSupportTest {
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    disposable = Disposer.newDisposable()

    val ijEpName = ExtensionPointName<VirtualFileManagerListener>("com.intellij.virtualFileManagerListener")
    ApplicationManager.getApplication().registerExtension(ijEpName, MyVirtualFileManagerListener, disposable)
  }

  @AfterEach
  fun afterEach() {
    Disposer.dispose(disposable)
  }

  @Test
  fun testSupport() {
    val extensionSupport = ExtensionSupport()
    val pointKey = ExtensionPointKey<VirtualFileManagerListener>("com.intellij.virtualFileManagerListener")
    val extensions = extensionSupport.getExtensions(pointKey)

    assert(MyVirtualFileManagerListener in extensions)
  }
}

private object MyVirtualFileManagerListener : VirtualFileManagerListener