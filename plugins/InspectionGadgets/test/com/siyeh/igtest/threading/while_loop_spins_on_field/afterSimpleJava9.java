// "Make 'b' volatile and add Thread.onSpinWait()" "true"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        while (b) Thread.onSpinWait();
    }
}