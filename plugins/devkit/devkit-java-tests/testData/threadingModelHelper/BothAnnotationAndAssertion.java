import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;

class BothAnnotationAndAssertion {
    @RequiresWriteLock
    void testMethod() {
        ThreadingAssertions.assertBackgroundThread();
    }
}