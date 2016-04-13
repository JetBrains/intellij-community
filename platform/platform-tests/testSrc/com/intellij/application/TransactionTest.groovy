package com.intellij.application

import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
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
    assert LaterInvocator.currentModalityState == ModalityState.NON_MODAL
    super.setUp()
    Registry.get("ide.require.transaction.for.model.changes").setValue(true)
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.dispatchAllInvocationEvents()
    Registry.get("ide.require.transaction.for.model.changes").resetToDefault()
    log.clear()
    LaterInvocator.leaveAllModals()
    super.tearDown()
  }

  public void "test write action in invokeLater requires transaction"() {
    assert app.isDispatchThread()
    assert !app.isWriteAccessAllowed()

    SwingUtilities.invokeLater { assertWritingProhibited() }
    UIUtil.dispatchAllInvocationEvents()
  }

  private void assertWritingProhibited() {
    boolean writeActionFailed = false
    def disposable = Disposer.newDisposable('assertWritingProhibited')
    LoggedErrorProcessor.instance.disableStderrDumping(disposable)
    try {
      app.runWriteAction { log << 'writing' }
    }
    catch (AssertionError ignore) {
      writeActionFailed = true
    }
    finally {
      Disposer.dispose(disposable)
    }
    if (!writeActionFailed) {
      fail('write action should fail')
    }
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
      assert !guard.contextTransaction
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
      def id = guard.contextTransaction
      assert id
      TransactionGuard.submitTransaction testRootDisposable, {
        log << '2'
        assert guard.contextTransaction
        assert id != guard.contextTransaction
      }
      assert log == ['1', '2']
    }
    assert log == ['1', '2']
  }

  public void "test modal progress started from inside a transaction has the same id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      def id = guard.contextTransaction
      assert id
      ProgressManager.instance.runProcessWithProgressSynchronously({
                                                                     assert !ApplicationManager.application.dispatchThread
                                                                     assert id == guard.contextTransaction
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
        assert !guard.contextTransaction
        log << '2'
      }).get()
    }
    assert log == ['1', '2']
  }

  public void "test do not merge transactions with null id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      guard.submitTransaction testRootDisposable, (TransactionId)null, { log << '2' }
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
      def id = guard.contextTransaction
      SwingUtilities.invokeLater {
        guard.submitTransaction testRootDisposable, id, { log << '4' }
      }
      guard.submitTransaction testRootDisposable, id, {
        UIUtil.dispatchAllInvocationEvents()
        log << '2'
        UIUtil.dispatchAllInvocationEvents()
        guard.submitTransaction testRootDisposable, id, { log << '5' }
        def nestedId = guard.contextTransaction
        SwingUtilities.invokeLater {
          guard.submitTransaction testRootDisposable, nestedId, { log << '3' }
          assert log == ['1', '2']
        }
        UIUtil.dispatchAllInvocationEvents()
      }
      assert log == ['1', '2', '3']
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1', '2', '3', '4', '5']
    }
  }

  public void "test submit with finished transaction id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      TransactionId id = null
      TransactionGuard.submitTransaction testRootDisposable, {
        log << '2'
        id = guard.contextTransaction
      }
      SwingUtilities.invokeLater {
        guard.submitTransaction testRootDisposable, id, { log << '3' }
      }
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1', '2', '3']
    }
  }

  public void "test write access in modal invokeLater"() {
    LaterInvocator.enterModal(new Object())
    UIUtil.dispatchAllInvocationEvents()
    def unsafeModality = ModalityState.current()

    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'

      LaterInvocator.enterModal(new Object())
      def safeModality = ModalityState.current()
      app.executeOnPooledThread({
        app.invokeLater({
                          app.runWriteAction { log << '2' }
                        }, safeModality)
        app.invokeLater({
                          assertWritingProhibited()
                          log << '4'
                        }, unsafeModality)
        app.invokeLater({
                          assertWritingProhibited()
                          log << '3'
                        }, ModalityState.any())
        app.invokeLater({ app.runWriteAction { log << '5' } }, ModalityState.NON_MODAL)
      }).get()
      UIUtil.dispatchAllInvocationEvents()
    }
    LaterInvocator.leaveAllModals()
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3', '4', '5']
  }

}
