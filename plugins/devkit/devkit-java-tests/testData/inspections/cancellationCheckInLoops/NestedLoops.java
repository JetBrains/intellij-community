package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Clazz {

  @RequiresReadLock
  public static void foo() {
    int j = 0;
    String[] items = {""};

    // nested loops of different kinds
    while (j < 100) {
      for (int i = 0; i < 5; i++) {
        for (String item : items) {
          <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
            doSomething();
            j++;
          } while (j < 5);
        }
      }
      j++;
    }

    // single line nested loops
    for (int i = 0; i < 5; i++) <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (String item : items) {
      doSomething();
    }
  }
}