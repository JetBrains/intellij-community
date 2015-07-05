import com.intellij.util.readWriteLock.LockProvided;
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

  @LockProvided(LockType.READ)
  private void readAnnotatedWithRead() {
    readMethod();
  }

  @LockProvided(LockType.READ)
  private void readAnnotatedWithWrite() {
    <warning descr="LOCK_REQUEST_LOST">writeMethod()</warning>;
  }

  @LockProvided(LockType.READ)
  private void <warning descr="LOCK_LEVEL_RAISED">readAnnotatedWithNothing</warning>() {
    // do nothing
  }

  private void notAnnotatedWithComplexCode() {
    int y = baz(bar(123));
  }

  @LockProvided(LockType.READ)
  private int <warning descr="LOCK_LEVEL_RAISED">bar</warning>(int val) {
    return 239 + val;
  }

  private int baz(int x) {
    return x * x;
  }

  @LockRequired(LockType.READ)
  @LockProvided(LockType.WRITE)
  private void <warning descr="BOTH_PROVIDED_AND_REQUIRE_USED">twoAnnotationsAtATime</warning>() {
    readMethod();
  }
}