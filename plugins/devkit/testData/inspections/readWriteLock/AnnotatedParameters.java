import com.intellij.util.readWriteLock.LockAnonymous;
import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Override;
import java.lang.Runnable;

class Test {
  private void foo(@LockAnonymous(LockType.READ) Runnable r1, Runnable r2) {
  }

  private void bar() {
    <warning descr="LOCK_LEVEL_RAISED">@LockAnonymous(LockType.READ) Runnable r1 = new Runnable() {
      @Override
      public void run() {
      }
    };</warning>

    Runnable r2 = new Runnable() {
      @Override
      public void run() {
      }
    };

    foo(r1, <warning descr="LOCK_REQUEST_LOST">r1</warning>);
    foo(<warning descr="LOCK_LEVEL_RAISED">r2</warning>, r2);
  }

}