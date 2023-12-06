package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        int i = 0;
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">do<caret></warning> {
            // check comments
        }
        while (i < 100);
    }
}