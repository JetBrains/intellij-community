import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.example.SubclassOfProcessCanceledException;

class IncorrectCeLoggedTests {
  private static final Logger LOG = Logger.getInstance(IncorrectCeLoggedTests.class);

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

  void testPceInheritorSwallowedAndLogged() {
    try {
      // anything
    } catch (SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
    }
  }

  void testPceInheritorSwallowedAndLoggedWhenDisjunctionTypeDefined() {
    try {
      // anything
    } catch (IllegalArgumentException | SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
    }
  }

  void testPceLoggedOutsideCatchBlock(ProcessCanceledException e) {
    <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
  }

  void testPceInheritorLoggedOutsideCatchBlock(com.example.SubclassOfProcessCanceledException e) {
    <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
  }

  void testPceLoggedAsSecondArgOutsideCatchBlock(ProcessCanceledException e) {
    <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error("something went wrong", e)</error>;
  }

  void testJucCeLoggedOutsideCatchBlock(java.util.concurrent.CancellationException e) {
    <error descr="'java.util.concurrent.CancellationException' must not be logged">LOG.error(e)</error>;
  }

  void testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

  void testRegularExceptionNotReported() {
    try {
      // anything
    } catch (RuntimeException e) {
      LOG.error(e);
    }
  }

  void testCeAssignedToThrowableNotReported() {
    Throwable t = new ProcessCanceledException();
    LOG.error(t);
  }

}
