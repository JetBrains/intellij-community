package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        String[] items = {""};

        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for<caret></warning> (String item : items) {
            doSomething();
        }
    }
}