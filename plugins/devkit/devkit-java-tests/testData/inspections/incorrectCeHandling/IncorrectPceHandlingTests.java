import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.CeProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.example.SubclassOfProcessCanceledException;
import com.example.SomeControlFlowException;
import java.util.concurrent.CancellationException;

class IncorrectPceHandlingTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingTests.class);

  void testPceSwallowed() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    }
  }

  void testPceLogged() {
    try {
      // anything
    } catch (ProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      throw e;
    }
  }

  void testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</error>;
    }
  }

  void testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.info(e)</error>;
    }
  }

  void testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
    }
  }

  void testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

  void testDisjunctionTypesWhenPceIsFirst() {
    try {
      // anything
    } catch (ProcessCanceledException | IllegalArgumentException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

  void testDisjunctionTypesWhenPceIsSecond() {
    try {
      // anything
    } catch (IllegalArgumentException | ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

  void testPceInheritorSwallowedAndLogger() {
    try {
      // anything
    } catch (SubclassOfProcessCanceledException <error descr="'com.example.SubclassOfProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.example.SubclassOfProcessCanceledException' must not be logged">LOG.error(e)</error>;
    }
  }

  void testPceInheritorSwallowedAndLoggerWhenDisjunctionTypeDefined() {
    try {
      // anything
    } catch (IllegalArgumentException | SubclassOfProcessCanceledException <error descr="'com.example.SubclassOfProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.example.SubclassOfProcessCanceledException' must not be logged">LOG.error(e)</error>;
    }
  }

  void testSwallowedAndLoggedOnWarnLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.warn(e)</error>;
    }
  }

  void testSwallowedAndLoggedOnDebugLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.debug(e)</error>;
    }
  }

  void testSwallowedAndLoggedWithDebug() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.warnWithDebug(e)</error>;
    }
  }

  void testRegularExceptionNotFlagged() {
    try {
      // anything
    } catch (RuntimeException e) {
      LOG.error(e);
    }
  }


  // IJPL-248976
  void testExceptionRethrownInParensNotFlagged() {
    try {
      // anything
    } catch (ProcessCanceledException e) {
      throw (e);
    }
  }

  // No call in the 'try' is proven to throw the current cancellation, so the 'CancellationException' may belong to an
  // awaited task.
  void testCancellationExceptionWithoutProvenSourceNotFlagged() {
    try {
      // anything
    } catch (CancellationException e) {
      // exception swallowed
    }
  }

  void testCancellationExceptionRethrownNotFlagged() {
    try {
      // anything
    } catch (CancellationException e) {
      throw e;
    }
  }

  // A 'checkCanceled()' call proves the caught 'CancellationException' can be the current computation's cancellation.
  void testCancellationExceptionWithProvenCurrentSourceSwallowed() {
    try {
      ProgressManager.checkCanceled();
    } catch (CancellationException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>) {
      // exception swallowed
    }
  }

  void testCancellationExceptionWrappedAndRethrownNotFlagged() {
    try {
      // anything
    } catch (CancellationException e) {
      throw new CeProcessCanceledException(e);
    }
  }

  // 'rethrowControlFlowException(e.getCause())' rethrows only the cause, not 'e', so it does NOT rethrow the caught exception.
  void testCancellationExceptionCauseRethrowStillFlagged() {
    try {
      ProgressManager.checkCanceled();
    } catch (CancellationException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'.">e</error>) {
      rethrowControlFlowException(e.getCause());
    }
  }

  // A 'checkCanceled()' in the catch body rethrows the current cancellation.
  void testPceRethrownByCheckCanceledNotFlagged() {
    try {
      // anything
    } catch (ProcessCanceledException e) {
      ProgressManager.checkCanceled();
    }
  }

  // A method that is only named like a cancellation check proves nothing.
  void testUnrelatedCheckCanceledNotFlagged() {
    try {
      checkCanceled();
    } catch (CancellationException e) {
      // exception swallowed
    }
  }

  void checkCanceled() {}

  void rethrowControlFlowException(Throwable t) { /* API stub */ }

  // A 'ControlFlowException' that is not a cancellation can be handled by design; it only must not be logged.
  void testControlFlowExceptionHandledNotFlagged() {
    try {
      // anything
    } catch (SomeControlFlowException e) {
      // exception handled
    }
  }

  void testBroadCatchRethrowNotFlagged() {
    try {
      // anything
    } catch (Throwable e) {
      LOG.error(e);
      throw e;
    }
  }

}
