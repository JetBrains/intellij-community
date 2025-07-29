import testutils.ThreadingAssertions;
import testutils.ExpectedPath

@ExpectedPath("MultipleAssertionsInMethod.testMethod -> ThreadingAssertions.assertReadAccess()")
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