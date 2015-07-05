import com.intellij.util.readWriteLock.LockAnonymous;
import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Override;
import java.lang.Runnable;

class Test {
  @LockRequired(LockType.READ)
  private void bar() {
    if (Math.random() > 1) {
      bar();
    }
  }

  @LockAnonymous(LockType.READ)
  private Runnable foo() {
    return new Runnable() {
      @LockRequired(LockType.READ)
      @Override
      public void run() {
        bar();
      }
    };
  }

  private void baz() {
    @LockAnonymous(LockType.READ) Runnable qoo = foo();
    <warning descr="LOCK_REQUEST_LOST">qoo.run()</warning>;
  }


}