package com.intellij.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.TransactionId
import com.intellij.openapi.progress.ProgressManager
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
  List<String> log = []

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
    log.clear()
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

    SwingUtilities.invokeLater { TransactionGuard.submitTransaction parent, { log << "1" } }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1']

    Disposer.dispose(parent)
    SwingUtilities.invokeLater { TransactionGuard.submitTransaction parent, { log << "2" } }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1']
  }

  public void "test no current id inside invokeLater"() {
    SwingUtilities.invokeLater {
      log << '2'
      assert !guard.currentMergeableTransaction
    }
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      UIUtil.dispatchAllInvocationEvents()
    }
    assert log == ['1', '2']
  }


  public void "test has id inside nested transaction"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      def id = guard.currentMergeableTransaction
      assert id
      TransactionGuard.submitTransaction testRootDisposable, {
        log << '2'
        assert guard.currentMergeableTransaction
        assert id != guard.currentMergeableTransaction
      }
      assert log == ['1', '2']
    }
    assert log == ['1', '2']
  }

  public void "test modal progress started from inside a transaction has the same id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      def id = guard.currentMergeableTransaction
      assert id
      ProgressManager.instance.runProcessWithProgressSynchronously({
                                                                     assert !ApplicationManager.application.dispatchThread
                                                                     assert id == guard.currentMergeableTransaction
                                                                     log << '2'
                                                                   }, 'title', true, project)
    }
    assert log == ['1', '2']
  }
  public void "test no id on pooled thread"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      ApplicationManager.application.executeOnPooledThread({
        assert !ApplicationManager.application.dispatchThread
        assert !guard.currentMergeableTransaction
        log << '2'
      }).get()
    }
    assert log == ['1', '2']
  }

  public void "test do not merge transactions with null id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      guard.submitMergeableTransaction testRootDisposable, (TransactionId)null, { log << '2' }
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1']
    }
    assert log == ['1']
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2']
  }

  public void "test do not merge into newly started nested transactions"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      def id = guard.currentMergeableTransaction
      SwingUtilities.invokeLater {
        guard.submitMergeableTransaction testRootDisposable, id, { log << '4' }
      }
      guard.submitMergeableTransaction testRootDisposable, id, {
        UIUtil.dispatchAllInvocationEvents()
        log << '2'
        UIUtil.dispatchAllInvocationEvents()
        guard.submitMergeableTransaction testRootDisposable, id, { log << '5' }
        def nestedId = guard.currentMergeableTransaction
        SwingUtilities.invokeLater {
          guard.submitMergeableTransaction testRootDisposable, nestedId, { log << '3' }
          assert log == ['1', '2']
        }
        UIUtil.dispatchAllInvocationEvents()
      }
      assert log == ['1', '2', '3']
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1', '2', '3', '4', '5']
    }
  }

}
