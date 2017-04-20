// "Make 'b' volatile and add Thread.onSpinWait()" "true"
import java.util.*;

public class Test {
    private boolean b;

    public void test() {
        whi<caret>le (b) {}
    }
}