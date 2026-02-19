package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.util.stream.Stream;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Stream<String> stream) {
    stream.forEachOrdered(e -> {
        ProgressManager.checkCanceled();
        doSomething();
    });
  }
}
