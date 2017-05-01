// "Make 'b' volatile" "true"
import java.util.*;

public class Test {
    private boolean b;

    public void test() {
        whi<caret>le (b) {
            Thread.onSpinWait();
        }
    }
}