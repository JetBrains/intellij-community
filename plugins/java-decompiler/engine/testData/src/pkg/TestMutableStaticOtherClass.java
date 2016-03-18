package pkg;

/**
 * @author Alexandru-Constantin Bledea
 * @since March 17, 2016
 */
public class TestMutableStaticOtherClass {

    private static final int SIZE;

    static {
        TestClassFields.staticMutable = 3;
        SIZE = TestClassFields.staticMutable;
    }
}
