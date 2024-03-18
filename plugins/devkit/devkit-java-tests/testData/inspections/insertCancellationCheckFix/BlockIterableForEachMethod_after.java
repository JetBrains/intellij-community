package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.util.List;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(List<String> iterable) {
    iterable.forEach(e -> {
        ProgressManager.checkCanceled();
        doSomething();
    });
  }
}
