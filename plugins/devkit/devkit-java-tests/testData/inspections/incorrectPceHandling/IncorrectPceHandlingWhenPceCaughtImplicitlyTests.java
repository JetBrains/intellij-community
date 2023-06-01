import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

class IncorrectPceHandlingWhenPceCaughtImplicitlyTests {
  private static final Logger LOG = Logger.getInstance(IncorrectPceHandlingWhenPceCaughtImplicitlyTests.class);

  void throwPce() throws ProcessCanceledException {
    // anything
  }

  void test1() {
    try {
      throwPce();
    } catch (Exception <error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      throwPce();
    } catch (Exception <error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.info("Error occured", e)</error>;
    }
  }

  void test3() {
    try {
      throwPce();
    } catch (Exception <error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.info(e)</error>;
    }
  }

  void test4() {
    try {
      throwPce();
    } catch (Exception <error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.error(e)</error>;
    }
  }

  void test5() {
    try {
      throwPce();
    } catch (Exception <error descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</error>) {
      <error descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.error("Error occurred: " + e.getMessage())</error>;
    }
  }

}
