package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        for (int i = 0; i < 5; i++) {
            ProgressManager.checkCanceled();
            // check comments
        }
    }
}