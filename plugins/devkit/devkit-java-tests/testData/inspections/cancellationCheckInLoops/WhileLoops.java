package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

  @RequiresReadLock
  public static void foo() {
    int i = 0;
    int j = 0;
    while (i < 100) {
      // nested loops with something in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 5) {
        doSomething();
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 10) {
          doSomething();
          i++;
        }
        i++;
      }

      // nested loops with a block in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 15) {
        if (i != 3) {
          //empty loop
          <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (j > 5) {
          }
        }
        i++;
      }

      // single-line loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 20) System.out.println(i);

      // no body loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning>(i < 0);

      i++;
    }
  }
}