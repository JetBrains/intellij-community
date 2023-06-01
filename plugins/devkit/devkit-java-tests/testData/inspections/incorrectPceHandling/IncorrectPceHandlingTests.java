import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class IncorrectPceHandlingTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingTests.class);

  void test1() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</error>;
    }
  }

  void test3() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.info(e)</error>;
    }
  }

  void test4() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.error(e)</error>;
    }
  }

  void test5() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

}
