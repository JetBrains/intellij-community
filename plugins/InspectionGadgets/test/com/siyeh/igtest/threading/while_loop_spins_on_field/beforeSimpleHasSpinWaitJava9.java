// "Make 'b' volatile" "true-preview"
import java.util.*;

public class Test {
    private boolean b;

    public void test() {
        whi<caret>le (b) {
            Thread.onSpinWait();
        }
    }
}