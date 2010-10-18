package org.jetbrains.android.ddms;

import com.android.ddmlib.AndroidDebugBridge;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

/**
 * @author Eugene.Kudelevsky
 */
public class AdbManager {
  private static final Object LOCK = new Object();
  private static final long WAIT_TIME = 5000;

  private AdbManager() {
  }

  private static class Wrapper<T> {
    T myWrappee;
  }

  public static <T> T compute(final Computable<T> computable, boolean restartIfCrashes) throws AdbNotRespondingException {
    final Wrapper<T> wrapper = new Wrapper<T>();
    final boolean[] finished = new boolean[] {false};
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        synchronized (LOCK) {
          wrapper.myWrappee = computable.compute();
          finished[0] = true;
          LOCK.notifyAll();
        }
      }
    });
    synchronized (LOCK) {
      long startTime = System.currentTimeMillis();
      while (!finished[0]) {
        long during = System.currentTimeMillis() - startTime;
        if (during >= WAIT_TIME) break;
        try {
          LOCK.wait(WAIT_TIME - during);
        }
        catch (InterruptedException e) {
        }
      }
    }
    if (!finished[0]) {
      if (restartIfCrashes) {
        run(new Runnable() {
          public void run() {
            AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
            if (bridge != null) {
              bridge.restart();
            }
          }
        }, false);
        compute(computable, false);
      }
      else {
        throw new AdbNotRespondingException();
      }
    }
    return wrapper.myWrappee;
  }

  public static void run(final Runnable runnable, boolean restartIfCrashes) throws AdbNotRespondingException {
    compute(new Computable<Object>() {
      public Object compute() {
        runnable.run();
        return null;
      }
    }, restartIfCrashes);
  }
}
