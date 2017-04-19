// "Declare field 'b' as 'volatile'" "true"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        int counter = 0;
        while (b) {
            if(counter++ > 100) {
                Thread.onSpinWait();
            }
        }
    }
}