package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Iterable<String> list) {
    ContainerUtil.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">pro<caret>cess</warning>(list, e -> {
        doSomething();
        return true;
    });
  }
}
