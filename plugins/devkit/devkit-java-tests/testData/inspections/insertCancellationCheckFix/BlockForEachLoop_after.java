package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

import static inspections.cancellationCheckInLoops.Foo.doSomething;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        String[] items = {""};

        for (String item : items) {
            ProgressManager.checkCanceled();
            doSomething();
        }
    }
}