import com.intellij.util.concurrency.ThreadingAssertions;

class MultipleAssertionsInMethod {
    void testMethod() {
        ThreadingAssertions.assertReadAccess();
        doSomething();
        ThreadingAssertions.assertReadAccess();
        if (condition()) {
            ThreadingAssertions.assertReadAccess();
        }
    }

    void doSomething() {}
    boolean condition() { return true; }
}