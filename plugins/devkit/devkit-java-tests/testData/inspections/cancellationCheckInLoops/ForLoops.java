package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

  @RequiresReadLock
  public static void foo() {
    for (int i = 0; i < 5; i++) {

      // nested loops with something in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int j = 0; j < 5; j++) {
        doSomething();
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int k = 0; k < 5; k++) {
          doSomething();
        }
      }

      // nested loops with a block in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int j = 0; j < 5; j++) {
        if (j != 3) {
          //empty loop
          <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int k = 0; k < 5; k++) {
          }
        }
      }

      // single-line loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int j = 0; j < 5; j++) System.out.println(j);

      // no body loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int y = 0; y < 10; y++);
    }
  }
}