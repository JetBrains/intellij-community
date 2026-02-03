package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ContainerUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Iterable<String> iterable, Iterator<String> iterator, List<String> list, Map<String, String> map, Stream<String> stream) {
    ContainerUtil.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">process</warning>(list, e -> {
      doSomething();
      return true;
    });

    iterable.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>(e -> {
      doSomething();
    });

    iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachRemaining</warning>(e -> {
      doSomething();
    });

    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>((k, v) -> {
      doSomething();
    });

    stream.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>(e -> {
      doSomething();
    });
    stream.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachOrdered</warning>(e -> {
      doSomething();
    });
    stream.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>(e -> doSomething());

    iterable.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning>(e -> {
      // empty block
    });
  }

  // No read lock required
  public static void foo2(Iterable<String> iterable, Iterator<String> iterator, List<String> list, Map<String, String> map, Stream<String> stream) {
    ContainerUtil.process(list, e -> {
      doSomething();
      return true;
    });

    iterable.forEach(e -> {
      doSomething();
    });

    iterator.forEachRemaining(e -> {
      doSomething();
    });

    map.forEach((k, v) -> {
      doSomething();
    });

    stream.forEach(e -> {
      doSomething();
    });
    stream.forEachOrdered(e -> {
      doSomething();
    });
    stream.forEach(e -> doSomething());

    iterable.forEach(e -> {
      // empty block
    });
  }
}