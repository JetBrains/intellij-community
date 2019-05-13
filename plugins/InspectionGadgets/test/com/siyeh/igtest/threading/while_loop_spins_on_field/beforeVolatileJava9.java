// "Add Thread.onSpinWait()" "true"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        whi<caret>le (b);
    }
}