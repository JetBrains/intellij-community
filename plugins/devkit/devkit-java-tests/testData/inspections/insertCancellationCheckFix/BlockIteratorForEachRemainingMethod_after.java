package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.util.Iterator;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Iterator<String> iterator) {
    iterator.forEachRemaining(e -> {
        ProgressManager.checkCanceled();
        doSomething();
    });
  }
}
