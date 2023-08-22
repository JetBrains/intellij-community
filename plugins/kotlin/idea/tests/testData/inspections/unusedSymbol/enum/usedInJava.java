import xxx.E5;
import xxx.E6;

import static xxx.E7.*;

public class Main {
    public void main() {
        E5.values();
        Runnable values = E6::values;
        values();
        E8.values();
    }
}