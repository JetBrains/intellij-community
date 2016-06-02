package com.intellij.application

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull

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
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    SwingUtilities.invokeLater(runnable)
    UIUtil.dispatchAllInvocationEvents()
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

  public void "test write action without transaction prohibited"() {
    assert app.isDispatchThread()
    assert !app.isWriteAccessAllowed()

    assertWritingProhibited()
    SwingUtilities.invokeLater { assertWritingProhibited() }
    UIUtil.dispatchAllInvocationEvents()
  }

  public void "test write action allowed inside user activity but not in modal dialog shown from non-modal invokeLater"() {
    SwingUtilities.invokeLater {
      guard.performUserActivity { app.runWriteAction { log << '1' } }

      LaterInvocator.enterModal(new Object())
      guard.performUserActivity {
        assertWritingProhibited()
        log << '2'
      }
      LaterInvocator.leaveAllModals()

      guard.performUserActivity { app.runWriteAction { log << '3' } }
    }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3']
  }

  private static void assertWritingProhibited() {
    boolean writeActionFailed = false
    def disposable = Disposer.newDisposable('assertWritingProhibited')
    LoggedErrorProcessor.instance.disableStderrDumping(disposable)
    try {
      app.runWriteAction {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.instance, false, true)
      }
    }
    catch (AssertionError ignore) {
      writeActionFailed = true
    }
    finally {
      Disposer.dispose(disposable)
    }
    if (!writeActionFailed) {
      fail('write action should fail ' + guard.toString())
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

  public void "test no context transaction inside invokeLater"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      SwingUtilities.invokeLater {
        log << '2'
        assert !guard.contextTransaction
      }
      log << '1'
      UIUtil.dispatchAllInvocationEvents()
      log << '3'
    }
    assert log == [] // the test is also run inside an invokeLater, so transaction is asynchronous
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3']
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
    UIUtil.dispatchAllInvocationEvents()
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
    UIUtil.dispatchAllInvocationEvents()
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
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2']
  }

  public void "test do not merge transactions with null id"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      guard.submitTransaction testRootDisposable, (TransactionId)null, { log << '2' }
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1']
    }
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
          String trace = guard.toString()
          guard.submitTransaction testRootDisposable, nestedId, {
            trace += "  " + IdeEventQueue.instance.trueCurrentEvent.toString() + "  " + DebugUtil.currentStackTrace()
            log << '3'
          }
          assert log == ['1', '2'] : log + " " + trace
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
        TransactionGuard.submitTransaction testRootDisposable, {
          log << '3'
          id = guard.contextTransaction
        }
      }
      SwingUtilities.invokeLater {
        guard.submitTransaction testRootDisposable, id, { log << '4' }
      }
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1', '2', '3', '4']
    }
  }

  public void "test write access in modal invokeLater"() {
    LaterInvocator.enterModal(new Object())
    UIUtil.dispatchAllInvocationEvents()
    def unsafeModality = ModalityState.current()

    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'

      def innerModal = new Object()
      LaterInvocator.enterModal(innerModal)
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
                          app.runWriteAction { log << '3' }
                        }, ModalityState.any())
        app.invokeLater({ app.runWriteAction { log << '5' } }, ModalityState.NON_MODAL)
      }).get()
      UIUtil.dispatchAllInvocationEvents()
      LaterInvocator.leaveModal(innerModal)
      UIUtil.dispatchAllInvocationEvents()
    }
    LaterInvocator.leaveAllModals()
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3', '4', '5']
  }

  public void "test submitTransactionLater happens ASAP regardless of modality bounds"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      guard.submitTransactionLater testRootDisposable, { log << '2' }
      LaterInvocator.enterModal(new Object())
      UIUtil.dispatchAllInvocationEvents()
      LaterInvocator.leaveAllModals()
      log << '3'
    }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3']
  }

  public void "test don't add transaction to outdated queue"() {
    TransactionGuard.submitTransaction testRootDisposable, {
      log << '1'
      guard.submitTransactionLater testRootDisposable, { log << '3' }
      log << '2'
    }
    TransactionGuard.submitTransaction testRootDisposable, {
      UIUtil.dispatchAllInvocationEvents()
      assert log == ['1', '2']
    }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', '2', '3']
  }

  public void "test no synchronous transactions inside invokeLater"() {
    LoggedErrorProcessor.instance.disableStderrDumping(testRootDisposable)
    SwingUtilities.invokeLater {
      log << '1'
      try {
        guard.submitTransactionAndWait { log << 'not run' }
      }
      catch (AssertionError ignore) {
        log << 'assert'
      }
    }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1', 'assert']
  }

  public void "test write-unsafe modality ends inside a transaction"() {
    LaterInvocator.enterModal(new Object())
    guard.performUserActivity { assertWritingProhibited() }
    TransactionGuard.submitTransaction testRootDisposable, {
      LaterInvocator.leaveAllModals()
      log << '1'
    }
    UIUtil.dispatchAllInvocationEvents()
    assert log == ['1']
    assert ModalityState.current() == ModalityState.NON_MODAL
    guard.performUserActivity { app.runWriteAction { log << '2' } }
    assert log == ['1', '2']
  }

}
