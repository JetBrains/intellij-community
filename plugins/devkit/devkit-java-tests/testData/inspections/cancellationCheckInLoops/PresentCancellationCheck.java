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

    for (int i = 0; i < 5; i++) {
      ProgressManager.checkCanceled();
      doSomething();
    }

    int i = 0;
    while (i < 5) {
      ProgressManager.checkCanceled();
      doSomething();
      i++;
    }

    do {
      ProgressManager.checkCanceled();
      doSomething();
      i++;
    } while (i < 10);
  }
}