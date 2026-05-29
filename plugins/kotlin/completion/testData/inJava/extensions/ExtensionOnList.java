package a;

import java.util.List;

public class Testing {
    public static void test() {
        List<String> list = List.of("a", "b");
        list.<caret>
    }
}
// EXIST: second
// ABSENT: wrongReceiver
