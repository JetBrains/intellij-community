package inspections.cancellationCheckInLoops;

import com.intellij.util.concurrency.annotations.RequiresReadLock;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be placed in the first line">for<caret></warning> (int i = 0; i < 5; i++) {
            // check comments
        }
    }
}