package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import java.util.stream.Stream;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Stream<String> stream) {
    stream.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for<caret>Each</warning>(e -> {
        doSomething();
    });
  }
}
