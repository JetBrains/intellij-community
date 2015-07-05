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

  private Runnable foo() {
    <warning descr="LOCK_REQUEST_LOST">return new Runnable() {
      @LockRequired(LockType.READ)
      @Override
      public void run() {
        bar();
      }
    };</warning>
  }

  @LockAnonymous(LockType.READ)
  private Runnable baz() {
    <warning descr="LOCK_LEVEL_RAISED">return new Runnable() {
      @Override
      public void run() {
      }
    };</warning>
  }


}