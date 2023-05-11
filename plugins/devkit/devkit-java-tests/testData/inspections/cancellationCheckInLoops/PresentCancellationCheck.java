package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.openapi.progress.ProgressManager;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

  @RequiresReadLock
  public static void foo() {
    String[] items = {""};
    for (String item : items) {
      ProgressManager.checkCanceled();
      doSomething();
    }
  }
}