import testutils.RequiresReadLock;
import testutils.ThreadingAssertions;
import test.ExpectedPath

@ExpectedPath("BothAnnotationAndAssertion.testMethod -> @RequiresReadLock")
@ExpectedPath("BothAnnotationAndAssertion.testMethod -> ThreadingAssertions.assertReadAccess()")
class BothAnnotationAndAssertion {
    @RequiresReadLock
    void testMethod() {
        ThreadingAssertions.assertReadAccess();
    }
}