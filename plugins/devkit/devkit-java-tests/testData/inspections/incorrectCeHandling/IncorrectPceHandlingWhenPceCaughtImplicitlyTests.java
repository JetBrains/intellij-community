import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.example.SubclassOfProcessCanceledException;

class IncorrectPceHandlingWhenPceCaughtImplicitlyTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingWhenPceCaughtImplicitlyTests.class);

  // tests for ProcessCanceledException

  void throwPce() throws ProcessCanceledException {
    // anything
  }

  void testPceSwallowed() {
    try {
      throwPce();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      // exception swallowed
    }
  }

  void testPceLogged() {
    try {
      throwPce();
    } catch (Exception e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error(e)</error>;
      throw e;
    }
  }

  void testSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwPce();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.info("Error occured", e)</error>;
    }
  }

  void testSwallowedAndLoggedOnInfoLevel() {
    try {
      throwPce();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.info(e)</error>;
    }
  }

  void testSwallowedAndLoggedOnErrorLevel() {
    try {
      throwPce();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error(e)</error>;
    }
  }

  void testSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwPce();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceIsFirst() {
    try {
      throwPce();
    } catch (RuntimeException | Error <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceIsSecond() {
    try {
      throwPce();
    } catch (Error | RuntimeException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testPceSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwPce();
    } catch (RuntimeException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'throwPce()'.">e</error>) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    } catch (Throwable e) {
      // exception swallowed
    }
  }

  void testPceLoggedAndMultipleGenericCatchClauses() {
    try {
      throwPce();
    } catch (RuntimeException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged. It is thrown by 'throwPce()'.">LOG.error(e)</error>;
      throw e;
    } catch (Exception e) {
      LOG.error(e);
      throw e;
    } catch (Throwable e) {
      LOG.error(e);
      throw e;
    }
  }

  void testNotHandlingOuterTryIfNestedCatchesPce() {
    try {
      // anything
      try {
        throwPce();
      }
      catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
        <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error(e)</error>;
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }



  // tests for ProcessCanceledException inheritor

  void throwPceInheritor() throws SubclassOfProcessCanceledException {
    // anything
  }

  void testPceInheritorSwallowed() {
    try {
      throwPceInheritor();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      // exception swallowed
    }
  }

  void testPceInheritorLogged() {
    try {
      throwPceInheritor();
    } catch (Exception e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error(e)</error>;
      throw e;
    }
  }

  void testPceInheritorSwallowedAndLoggedWithMessageOnInfoLevel() {
    try {
      throwPceInheritor();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.info("Error occured", e)</error>;
    }
  }

  void testPceInheritorSwallowedAndLoggedOnInfoLevel() {
    try {
      throwPceInheritor();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.info(e)</error>;
    }
  }

  void testPceInheritorSwallowedAndLoggedOnErrorLevel() {
    try {
      throwPceInheritor();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error(e)</error>;
    }
  }

  void testPceInheritorSwallowedAndOnlyExceptionMessageLogged() {
    try {
      throwPceInheritor();
    } catch (Exception <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceInheritorIsFirst() {
    try {
      throwPceInheritor();
    } catch (RuntimeException | Error <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceInheritorIsSecond() {
    try {
      throwPceInheritor();
    } catch (Error | RuntimeException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testPceInheritorSwallowedAndMultipleGenericCatchClauses() {
    try {
      throwPceInheritor();
    } catch (RuntimeException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown. It is thrown by 'throwPceInheritor()'.">e</error>) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    } catch (Throwable e) {
      // exception swallowed
    }
  }

  void testPceInheritorLoggedAndMultipleGenericCatchClauses() {
    try {
      throwPceInheritor();
    } catch (RuntimeException e) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged. It is thrown by 'throwPceInheritor()'.">LOG.error(e)</error>;
      throw e;
    } catch (Exception e) {
      LOG.error(e);
      throw e;
    } catch (Throwable e) {
      LOG.error(e);
      throw e;
    }
  }

  void testNotHandlingOuterTryIfNestedCatchesPceInheritor() {
    try {
      // anything
      try {
        throwPceInheritor();
      }
      catch (SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
        <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

}
