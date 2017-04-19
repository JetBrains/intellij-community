// "Declare field 'b' as 'volatile'" "true"
import java.util.*;

public class Test {
    private boolean b;

    public void test() {
        int counter = 0;
        whi<caret>le (b) {
            if(counter++ > 100) {
                Thread.onSpinWait();
            }
        }
    }
}