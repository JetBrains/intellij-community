package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        for<caret> (int i = 0; i < 5; i++) {
            ProgressManager.checkCanceled();
            ;
        }
    }
}