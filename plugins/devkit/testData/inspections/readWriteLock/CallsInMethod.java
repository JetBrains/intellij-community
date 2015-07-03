import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Math;
import java.lang.Runnable;

class Test {
  @LockRequired(LockType.READ)
  private void readMethod() {
    if (Math.random() > 1) {
      readMethod();
    }
  }

  @LockRequired(LockType.WRITE)
  private void writeMethod() {
    if (Math.random() > 1) {
      writeMethod();
    }
  }

  private void notAnnotated() {
    <warning descr="LOCK_REQUEST_LOST">readMethod()</warning>;
  }

  @LockRequired(LockType.READ)
  private void readAnnotatedWithRead() {
    readMethod();
  }

  @LockRequired(LockType.READ)
  private void readAnnotatedWithWrite() {
    <warning descr="LOCK_REQUEST_LOST">writeMethod()</warning>;
  }

  @LockRequired(LockType.READ)
  private void <warning descr="LOCK_LEVEL_RAISED">readAnnotatedWithNothing</warning>() {
    // do nothing
  }

  private void notAnnotatedWithComplexCode() {
    int y = baz(<warning descr="LOCK_REQUEST_LOST">bar(123)</warning>);
  }

  @LockRequired(LockType.READ)
  private int <warning descr="LOCK_LEVEL_RAISED">bar</warning>(int val) {
    return 239 + val;
  }

  private int baz(int x) {
    return x * x;
  }
}