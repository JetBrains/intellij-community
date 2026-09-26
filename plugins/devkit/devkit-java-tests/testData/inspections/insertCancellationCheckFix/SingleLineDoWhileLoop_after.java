package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        int i = 0;
        do {
            ProgressManager.checkCanceled();
            i++;
        }
        while (i < 100);
    }
}