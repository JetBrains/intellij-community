package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Iterable<String> iterable, Iterator<String> iterator, List<String> list) {
    ContainerUtil.process(list, a -> {
      iterable.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>(b -> {
        doSomething();
      });
      return true;
    });

    for (String a : iterable) {
      iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachRemaining</warning>(b -> {
        doSomething();
      });
    }

    iterable.forEach(a -> {
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (String b : iterable) {
        doSomething();
      }
    });
  }
}
