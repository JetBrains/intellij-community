import com.intellij.util.readWriteLock.LockRequired;
import com.intellij.util.readWriteLock.LockType;

import java.lang.Runnable;

class Test {
  private void assignReadRequiringToWriteRequiring(@LockRequired(LockType.READ) Runnable foo) {
    <warning descr="LOCK_LEVEL_RAISED">@LockRequired(LockType.WRITE) Runnable bar = foo;</warning>
  }

  private void assignWriteRequiringToReadRequiring(@LockRequired(LockType.WRITE) Runnable foo) {
    <warning descr="LOCK_REQUEST_LOST">@LockRequired(LockType.READ) Runnable bar = foo;</warning>
  }
}