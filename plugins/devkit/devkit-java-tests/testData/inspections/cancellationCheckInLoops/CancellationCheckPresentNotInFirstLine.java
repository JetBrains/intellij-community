package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.openapi.progress.ProgressManager;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

  @RequiresReadLock
  public static void foo() {
    String[] items = {""};
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (String item : items) {
      doSomething();
      ProgressManager.checkCanceled();
    }

    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (int i = 0; i < 5; i++) {
      doSomething();
      ProgressManager.checkCanceled();
    }

    int i = 0;
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">while</warning> (i < 5) {
      doSomething();
      ProgressManager.checkCanceled();
      i++;
    }

    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do</warning> {
      doSomething();
      i++;
      ProgressManager.checkCanceled();
    } while (i < 10);
  }
}