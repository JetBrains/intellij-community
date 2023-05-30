import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class IncorrectPceHandlingTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingTests.class);

  void test1() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</warning>;
    }
  }

  void test3() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.info(e)</warning>;
    }
  }

  void test4() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.error(e)</warning>;
    }
  }

  void test5() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</warning>;
    }
  }

}
