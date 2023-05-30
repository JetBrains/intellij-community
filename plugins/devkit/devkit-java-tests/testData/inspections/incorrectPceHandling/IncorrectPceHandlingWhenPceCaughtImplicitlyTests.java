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
    } catch (Exception <warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>) {
      // exception swallowed
    }
  }

  void test2() {
    try {
      throwPce();
    } catch (Exception <warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.info("Error occured", e)</warning>;
    }
  }

  void test3() {
    try {
      throwPce();
    } catch (Exception <warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.info(e)</warning>;
    }
  }

  void test4() {
    try {
      throwPce();
    } catch (Exception <warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.error(e)</warning>;
    }
  }

  void test5() {
    try {
      throwPce();
    } catch (Exception <warning descr="'ProcessCanceledException' must be rethrown. 'ProcessCanceledException' is thrown by 'throwPce()'.">e</warning>) {
      <warning descr="'ProcessCanceledException' must not be logged. 'ProcessCanceledException' is thrown by 'throwPce()'.">LOG.error("Error occurred: " + e.getMessage())</warning>;
    }
  }

}
