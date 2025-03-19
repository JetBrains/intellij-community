package inspections.cancellationCheckInLoops

import com.intellij.util.concurrency.annotations.RequiresReadLock

import inspections.cancellationCheckInLoops.Foo.doSomething


abstract class Bar {

  @RequiresReadLock
  abstract fun bar()

}

class BarImpl : Bar() {

  override fun bar() {
    <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (i in 1..10) {
      doSomething()
    }
  }

}