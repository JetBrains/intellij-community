package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo(Iterable<String> iterable, List<String> list) {
    ContainerUtil.process(list, a -> {
      iterable.forEach(b -> {
          ProgressManager.checkCanceled();
          doSomething();
      });
      return true;
    });
  }
}
