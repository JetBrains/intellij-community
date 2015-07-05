import com.intellij.util.readWriteLock.LockAnonymous;
import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Override;
import java.lang.Runnable;

class Test {
  private void foo(@LockAnonymous(LockType.READ) Runnable runnable) {
    <warning descr="LOCK_REQUEST_LOST">runnable.run()</warning>;
  }
}