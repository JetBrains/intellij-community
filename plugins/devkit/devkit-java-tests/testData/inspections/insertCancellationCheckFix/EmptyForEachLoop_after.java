package inspections.cancellationCheckInLoops;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;


class Clazz {

    @RequiresReadLock
    public static void foo() {
        String[] items = {""};

        for (String item : items) {
            ProgressManager.checkCanceled();
            // check comments
        }
    }
}