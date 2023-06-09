package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

class Bar {
  public void bar() {
    String[] items = {""};
    for (String item : items) {
      doSomething();
    }
  }

  @RequiresReadLock
  public void barReadLock() {
    String[] items = {""};
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (String item : items) {
      doSomething();
    }
  }

}