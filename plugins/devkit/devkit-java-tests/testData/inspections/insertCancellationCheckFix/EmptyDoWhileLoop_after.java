package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        int i = 0;
        do {
            ProgressManager.checkCanceled();
            // check comments
        }
        while (i < 100);
    }
}