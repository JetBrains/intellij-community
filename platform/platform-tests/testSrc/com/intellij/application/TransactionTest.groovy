package com.intellij.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ui.UIUtil

import javax.swing.*
/**
 * @author peter
 */
class TransactionTest extends LightPlatformTestCase {
  static TransactionGuardImpl getGuard() {
    return TransactionGuard.getInstance() as TransactionGuardImpl
  }

  static Application getApp() {
    return ApplicationManager.getApplication()
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    Registry.get("ide.require.transaction.for.model.changes").setValue(true)
    LoggedErrorProcessor.instance.disableStderrDumping(testRootDisposable)
  }

  @Override
  protected void tearDown() throws Exception {
    Registry.get("ide.require.transaction.for.model.changes").resetToDefault()
    super.tearDown()
  }

  public void "test write action in invokeLater requires transaction"() {
    assert app.isDispatchThread()
    assert !app.isWriteAccessAllowed()

    SwingUtilities.invokeLater {
      try {
        app.runWriteAction {}
        fail()
      }
      catch (AssertionError ignore) {
        // a trace is also printed to stderr, which is expected
      }
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  public void "test parent disposable"() {
    def parent = Disposer.newDisposable()
    def log = []

    SwingUtilities.invokeLater { TransactionGuard.submitTransaction parent, { log << "1" } }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1']

    Disposer.dispose(parent)
    SwingUtilities.invokeLater { TransactionGuard.submitTransaction parent, { log << "2" } }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1']
  }
}
