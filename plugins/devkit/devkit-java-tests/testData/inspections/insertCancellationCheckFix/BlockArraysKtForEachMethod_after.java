package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import kotlin.collections.ArraysKt;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(String[] array) {
    ArraysKt.forEach(array, e -> {
        ProgressManager.checkCanceled();
        doSomething();
        return null;
    });
  }
}
