import org.junit.*;

import static org.junit.Assert.assertTrue;

public class BoxedComparisonToEquals {
    void test(Integer a, int b) {
        <caret>assertTrue(a == b);
    }
}