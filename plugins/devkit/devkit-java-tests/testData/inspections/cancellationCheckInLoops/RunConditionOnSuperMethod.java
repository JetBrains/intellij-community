package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;

abstract class Bar {

  @RequiresReadLock
  public abstract void bar();

}


class BarImpl extends Bar {

  @Override
  public void bar() {
    String[] items = {""};
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (String item : items) {
      doSomething();
    }
  }

}