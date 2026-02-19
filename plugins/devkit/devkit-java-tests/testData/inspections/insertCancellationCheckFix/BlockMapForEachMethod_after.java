package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.util.Map;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Map<String, String> map) {
    map.forEach((k, v) -> {
        ProgressManager.checkCanceled();
        doSomething();
    });
  }
}
