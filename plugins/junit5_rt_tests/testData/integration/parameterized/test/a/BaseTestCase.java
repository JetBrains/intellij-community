package a;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public abstract class BaseTestCase extends TestCase {

    @Parameterized.Parameters
    public static List<Integer> params() {
        return Arrays.asList(1, 2);
    }

    protected int myField;
    public BaseTestCase(int i) {
        myField = i;
    }

    @Test
    public void simple() throws Exception {
    }
}
