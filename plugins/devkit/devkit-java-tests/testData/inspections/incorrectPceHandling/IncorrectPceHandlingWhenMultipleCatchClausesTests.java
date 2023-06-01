import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class IncorrectPceHandlingWhenMultipleCatchClausesTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingWhenMultipleCatchClausesTests.class);

  void test1() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      // exception swallowed
    } catch (Exception e) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.info("Error occured", e)</error>;
    } catch (Exception e) {
      LOG.info("Error occured", e);
    }
  }

  void test3() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.info(e)</error>;
    } catch (Exception e) {
      LOG.info(e);
    }
  }

  void test4() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.error(e)</error>;
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  void test5() {
    try {
      // anything
    } catch (ProcessCanceledException <error descr="'ProcessCanceledException' must be rethrown">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged">LOG.error("Error occurred: " + e.getMessage())</error>;
    } catch (Exception e) {
      LOG.error("Error occurred: " + e.getMessage());
    }
  }

}
