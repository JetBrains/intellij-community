package foo;

import junit.framework.TestCase;

public class FooTest extends TestCase {
    public void testMethod1() {
        new FooClass().method1();
    }

    public void testMethod2() {
        new FooClass().method2(true);
    }
}
