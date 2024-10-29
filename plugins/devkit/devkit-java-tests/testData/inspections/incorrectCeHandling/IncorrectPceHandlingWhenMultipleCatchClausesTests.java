import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.example.SubclassOfProcessCanceledException;

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingWhenMultipleCatchClausesTests.class);

  void testPceSwallowed() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    }
  }

  void testPceLogged() {
    try {
      // anything
    } catch (ProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      throw e;
    } catch (Exception e) {
      // exception swallowed
    }
  }

  void testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</error>;
    } catch (Exception e) {
      LOG.info("Error occured", e);
    }
  }

  void testSwallowedAndLoggedOnInfoLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.info(e)</error>;
    } catch (Exception e) {
      LOG.info(e);
    }
  }

  void testSwallowedAndLoggedOnErrorLevel() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  void testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    } catch (Exception e) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

  void testPceSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    } catch (RuntimeException e) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    } catch (Throwable e) {
      // exception swallowed
    }
  }

  void testPceLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    } catch (ProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      throw e;
    } catch (RuntimeException e) {
      LOG.error(e);
    } catch (Exception e) {
      LOG.error(e);
    } catch (Throwable e) {
      LOG.error(e);
    }
  }

  void testPceInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      // anything
    } catch (SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
      // exception swallowed
    } catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    } catch (RuntimeException e) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    } catch (Throwable e) {
      // exception swallowed
    }
  }

  void testPceInheritorLoggedAndMultipleGenericCatchClauses() {
    try {
      // anything
    } catch (SubclassOfProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
      throw e;
    } catch (ProcessCanceledException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      throw e;
    } catch (RuntimeException e) {
      LOG.error(e);
    } catch (Exception e) {
      LOG.error(e);
    } catch (Throwable e) {
      LOG.error(e);
    }
  }

  void testNotHandlingOuterTryIfNestedCatchesPce() {
    try {
      // anything
      try {
        // anything
      }
      catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
        <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

}
