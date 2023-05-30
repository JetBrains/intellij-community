import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingWhenMultipleCatchClausesTests.class);

  void test1() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</warning>;
    } catch (Exception e) {
      LOG.info("Error occured", e);
    }
  }

  void test3() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.info(e)</warning>;
    } catch (Exception e) {
      LOG.info(e);
    }
  }

  void test4() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.error(e)</warning>;
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  void test5() {
    try {
      // anything
    } catch (ProcessCanceledException <warning descr="'ProcessCanceledException' must be rethrown">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</warning>;
    } catch (Exception e) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

}
