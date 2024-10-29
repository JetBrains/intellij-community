import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.example.SubclassOfProcessCanceledException;

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
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceIsFirst() {
    try {
      // anything
    } catch (ProcessCanceledException | IllegalStateException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testDisjunctionTypesWhenPceIsSecond() {
    try {
      // anything
    } catch (IllegalStateException | ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

  void testPceInheritorSwallowedAndLogger() {
    try {
      // anything
    } catch (SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
    }
  }

  void testPceInheritorSwallowedAndLoggerWhenDisjunctionTypeDefined() {
    try {
      // anything
    } catch (IllegalStateException | SubclassOfProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must be rethrown">e</error>) {
      <error descr="'com.intellij.openapi.progress.ProcessCanceledException' inheritor must not be logged">LOG.error(e)</error>;
    }
  }

}
