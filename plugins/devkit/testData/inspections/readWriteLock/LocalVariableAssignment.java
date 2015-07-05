import com.intellij.util.readWriteLock.LockAnonymous;
import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Runnable;

class Test {
  private void assignReadRequiringToWriteRequiring(@LockAnonymous(LockType.READ) Runnable foo) {
    <warning descr="LOCK_LEVEL_RAISED">@LockAnonymous(LockType.WRITE) Runnable bar = foo;</warning>
  }

  private void assignWriteRequiringToReadRequiring(@LockAnonymous(LockType.WRITE) Runnable foo) {
    <warning descr="LOCK_REQUEST_LOST">@LockAnonymous(LockType.READ) Runnable bar = foo;</warning>
  }
}