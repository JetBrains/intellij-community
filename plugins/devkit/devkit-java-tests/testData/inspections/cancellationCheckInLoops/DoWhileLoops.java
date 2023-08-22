package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

  @RequiresReadLock
  public static void foo() {
    int i = 0;
    int j = 0;
    do {
      // nested loops with something in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
        doSomething();
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
          doSomething();
          i++;
        } while (i < 10);
        i++;
      } while (i < 5);

      // nested loops with a block in between
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
        if (i != 3) {
          //empty loop
          <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
          } while (j > 5);
        }
        i++;
      } while (i < 15);

      // single-line loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> System.out.println(i); while (i < 20);

      // no body loop
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning>; while(i < 0);

      i++;
    } while (i < 100);
  }
}